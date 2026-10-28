package syncmodel

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"unicode/utf8"
)

const (
	packetMagic = uint32(0x47545233)
	maxPacket   = 32 * 1024 * 1024
	maxRecords  = 100_000
	maxParents  = 1024
	maxText     = 1024 * 1024
)

func Encode(revisions []Revision) ([]byte, error) {
	if len(revisions) > maxRecords {
		return nil, errors.New("too many revisions")
	}
	b := new(bytes.Buffer)
	put := func(v uint32) { _ = binary.Write(b, binary.BigEndian, v) }
	text := func(v string) error {
		if len(v) > maxText || !utf8.ValidString(v) {
			return errors.New("invalid text")
		}
		put(uint32(len(v)))
		b.WriteString(v)
		return nil
	}
	put(packetMagic)
	put(1)
	put(uint32(len(revisions)))
	for _, raw := range revisions {
		r, err := normalize(raw)
		if err != nil {
			return nil, err
		}
		for _, value := range []string{r.ID, r.RecordID, r.DeviceID, r.Kind} {
			if err = text(value); err != nil {
				return nil, err
			}
		}
		if len(r.Parents) > maxParents {
			return nil, errors.New("too many parents")
		}
		put(uint32(len(r.Parents)))
		for _, parent := range r.Parents {
			if err = text(parent); err != nil {
				return nil, err
			}
		}
		if r.Deleted {
			b.WriteByte(1)
		} else {
			b.WriteByte(0)
		}
		if err = text(r.Payload); err != nil {
			return nil, err
		}
		if b.Len() > maxPacket {
			return nil, errors.New("revision packet too large")
		}
	}
	return b.Bytes(), nil
}

func Decode(data []byte) ([]Revision, error) {
	if len(data) > maxPacket {
		return nil, errors.New("revision packet too large")
	}
	b := bytes.NewReader(data)
	read := func(max uint32) (uint32, error) {
		var v uint32
		if err := binary.Read(b, binary.BigEndian, &v); err != nil {
			return 0, err
		}
		if v > max {
			return 0, errors.New("invalid field size")
		}
		return v, nil
	}
	text := func() (string, error) {
		n, err := read(maxText)
		if err != nil {
			return "", err
		}
		if uint64(n) > uint64(b.Len()) {
			return "", io.ErrUnexpectedEOF
		}
		v := make([]byte, n)
		if _, err = io.ReadFull(b, v); err != nil {
			return "", err
		}
		if !utf8.Valid(v) {
			return "", errors.New("invalid UTF-8")
		}
		return string(v), nil
	}
	magic, err := read(^uint32(0))
	if err != nil || magic != packetMagic {
		return nil, errors.New("invalid revision packet")
	}
	version, err := read(1)
	if err != nil || version != 1 {
		return nil, errors.New("unsupported revision protocol")
	}
	count, err := read(maxRecords)
	if err != nil {
		return nil, err
	}
	result := make([]Revision, 0)
	for i := uint32(0); i < count; i++ {
		var r Revision
		for _, field := range []*string{&r.ID, &r.RecordID, &r.DeviceID, &r.Kind} {
			if *field, err = text(); err != nil {
				return nil, err
			}
		}
		n, readErr := read(maxParents)
		if readErr != nil {
			return nil, readErr
		}
		r.Parents = make([]string, 0)
		for p := uint32(0); p < n; p++ {
			value, textErr := text()
			if textErr != nil {
				return nil, textErr
			}
			r.Parents = append(r.Parents, value)
		}
		marker, readErr := b.ReadByte()
		if readErr != nil || marker > 1 {
			return nil, errors.New("invalid deletion marker")
		}
		r.Deleted = marker == 1
		if r.Payload, err = text(); err != nil {
			return nil, err
		}
		r, err = normalize(r)
		if err != nil {
			return nil, err
		}
		result = append(result, r)
	}
	if b.Len() != 0 {
		return nil, errors.New("unexpected trailing data")
	}
	return result, nil
}
