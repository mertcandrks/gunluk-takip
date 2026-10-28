package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"github.com/grandcat/zeroconf"
	"golang.org/x/crypto/pbkdf2"
)

const (
	syncPort      = 47832
	wireMagic     = uint32(0x47545331)
	ackMagic      = uint32(0x47544F4B)
	maxPacketSize = 32 * 1024 * 1024
)

var additionalData = []byte("GunlukTakip-Sync-v1")

type SyncServer struct {
	mu         sync.RWMutex
	code       string
	serviceID  string
	startupErr error
	discovery  *zeroconf.Server
	closed     bool
	listener   net.Listener
	onSnapshot func(*Snapshot, []byte) ([]byte, error)
	onStatus   func(string)
}

func NewSyncServer(onSnapshot func(*Snapshot, []byte) ([]byte, error), onStatus func(string)) *SyncServer {
	identity, err := loadPairingIdentity()
	return &SyncServer{code: identity.Code, serviceID: identity.ID, startupErr: err, onSnapshot: onSnapshot, onStatus: onStatus}
}
func (s *SyncServer) ServiceID() string   { return s.serviceID }
func (s *SyncServer) PairingCode() string { s.mu.RLock(); defer s.mu.RUnlock(); return s.code }
func (s *SyncServer) Run() {
	if s.startupErr != nil {
		s.onStatus("Eşleşme bilgisi okunamadı: " + s.startupErr.Error())
		return
	}
	listener, err := net.Listen("tcp", fmt.Sprintf(":%d", syncPort))
	if err != nil {
		s.onStatus("Bağlantı açılamadı: " + err.Error())
		return
	}
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		_ = listener.Close()
		return
	}
	s.listener = listener
	advertiser, discoveryErr := zeroconf.Register("GunlukTakip-"+s.serviceID, "_gunluktakip._tcp", "local.", syncPort, []string{"id=" + s.serviceID, "v=1"}, nil)
	s.discovery = advertiser
	s.mu.Unlock()
	if discoveryErr != nil {
		s.onStatus("Telefon bekleniyor; otomatik keşif kullanılamıyor")
	} else {
		s.onStatus("Telefon bekleniyor")
	}
	for {
		connection, acceptErr := listener.Accept()
		if acceptErr != nil {
			if !errors.Is(acceptErr, net.ErrClosed) {
				s.onStatus("Bağlantı kesildi")
			}
			return
		}
		if err = s.receive(connection); err != nil {
			s.onStatus(err.Error())
		}
	}
}
func (s *SyncServer) receive(connection net.Conn) error {
	defer connection.Close()
	_ = connection.SetDeadline(time.Now().Add(20 * time.Second))
	plain, err := readSecurePacket(connection, s.PairingCode())
	if err != nil {
		return err
	}
	snapshot, err := decodeSnapshot(plain)
	if err != nil {
		return err
	}
	merged, mergeErr := s.onSnapshot(snapshot, plain)
	if mergeErr != nil {
		return errors.New("yerel kopya kaydedilemedi")
	}
	if err = binary.Write(connection, binary.BigEndian, ackMagic); err != nil {
		return err
	}
	if err = writeSecurePacket(connection, merged, s.PairingCode()); err != nil {
		return err
	}
	s.onStatus("Senkron tamamlandı")
	return nil
}

func writeSecurePacket(writer io.Writer, plain []byte, code string) error {
	salt := make([]byte, 16)
	nonce := make([]byte, 12)
	if _, err := rand.Read(salt); err != nil {
		return err
	}
	if _, err := rand.Read(nonce); err != nil {
		return err
	}
	key := pbkdf2.Key([]byte(code), salt, 120_000, 32, sha256.New)
	block, err := aes.NewCipher(key)
	if err != nil {
		return err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return err
	}
	encrypted := gcm.Seal(nil, nonce, plain, additionalData)
	if len(encrypted) > maxPacketSize {
		return errors.New("paket çok büyük")
	}
	if err = binary.Write(writer, binary.BigEndian, wireMagic); err != nil {
		return err
	}
	if _, err = writer.Write(salt); err != nil {
		return err
	}
	if _, err = writer.Write(nonce); err != nil {
		return err
	}
	if err = binary.Write(writer, binary.BigEndian, int32(len(encrypted))); err != nil {
		return err
	}
	_, err = writer.Write(encrypted)
	return err
}
func (s *SyncServer) Close() {
	s.mu.Lock()
	s.closed = true
	listener := s.listener
	discovery := s.discovery
	s.mu.Unlock()
	if listener != nil {
		_ = listener.Close()
	}
	if discovery != nil {
		discovery.Shutdown()
	}
}

func readSecurePacket(reader io.Reader, code string) ([]byte, error) {
	var magic uint32
	if err := binary.Read(reader, binary.BigEndian, &magic); err != nil || magic != wireMagic {
		return nil, errors.New("geçersiz bağlantı")
	}
	salt := make([]byte, 16)
	nonce := make([]byte, 12)
	if _, err := io.ReadFull(reader, salt); err != nil {
		return nil, errors.New("eksik güvenlik bilgisi")
	}
	if _, err := io.ReadFull(reader, nonce); err != nil {
		return nil, errors.New("eksik güvenlik bilgisi")
	}
	var size int32
	if err := binary.Read(reader, binary.BigEndian, &size); err != nil || size < 16 || size > maxPacketSize {
		return nil, errors.New("geçersiz paket boyutu")
	}
	encrypted := make([]byte, size)
	if _, err := io.ReadFull(reader, encrypted); err != nil {
		return nil, errors.New("eksik paket")
	}
	key := pbkdf2.Key([]byte(code), salt, 120_000, 32, sha256.New)
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	var gcm cipher.AEAD
	if gcm, err = cipher.NewGCM(block); err != nil {
		return nil, err
	}
	plain, err := gcm.Open(nil, nonce, encrypted, additionalData)
	if err != nil {
		return nil, errors.New("eşleştirme kodu yanlış veya paket bozuk")
	}
	return plain, nil
}
func newPairingCode() string {
	value, err := rand.Int(rand.Reader, big.NewInt(100_000_000))
	if err != nil {
		return "00000000"
	}
	return fmt.Sprintf("%08d", value.Int64())
}

// GUNLUK_TAKIP_PAIRING_CODE is a development hook for repeatable device sync tests.
// End-user sessions keep using a newly generated eight-digit code.
func configuredPairingCode() string {
	code := os.Getenv("GUNLUK_TAKIP_PAIRING_CODE")
	if len(code) != 8 {
		return newPairingCode()
	}
	for _, character := range code {
		if character < '0' || character > '9' {
			return newPairingCode()
		}
	}
	return code
}
func localAddresses() []string {
	var addresses []string
	interfaces, _ := net.Interfaces()
	for _, item := range interfaces {
		if item.Flags&net.FlagUp == 0 || item.Flags&net.FlagLoopback != 0 {
			continue
		}
		items, _ := item.Addrs()
		for _, raw := range items {
			ip, _, err := net.ParseCIDR(raw.String())
			if err == nil && ip.To4() != nil && ip.IsPrivate() {
				addresses = append(addresses, ip.String())
			}
		}
	}
	sort.Strings(addresses)
	return addresses
}
func snapshotPath() (string, error) {
	directory, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	directory = filepath.Join(directory, "GunlukTakipPC")
	if err = os.MkdirAll(directory, 0700); err != nil {
		return "", err
	}
	return filepath.Join(directory, "last-sync.gts"), nil
}
func saveSnapshot(data []byte) error {
	path, err := snapshotPath()
	if err != nil {
		return err
	}
	temporary := path + ".tmp"
	if err = os.WriteFile(temporary, data, 0600); err != nil {
		return err
	}
	return os.Rename(temporary, path)
}
func loadSnapshot() (*Snapshot, error) {
	path, err := snapshotPath()
	if err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return decodeSnapshot(data)
}
