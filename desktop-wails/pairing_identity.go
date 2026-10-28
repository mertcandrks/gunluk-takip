package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
)

type pairingIdentity struct {
	ID   string `json:"id"`
	Code string `json:"code"`
}

func loadPairingIdentity() (pairingIdentity, error) {
	path, err := snapshotPath()
	if err != nil {
		return pairingIdentity{}, err
	}
	path = filepath.Join(filepath.Dir(path), "pairing.json")
	raw, err := os.ReadFile(path)
	if err == nil {
		var identity pairingIdentity
		if json.Unmarshal(raw, &identity) != nil || len(identity.ID) != 32 || len(identity.Code) != 8 {
			return identity, errors.New("invalid pairing identity")
		}
		if _, err := hex.DecodeString(identity.ID); err != nil {
			return identity, err
		}
		for _, c := range identity.Code {
			if c < '0' || c > '9' {
				return identity, errors.New("invalid pairing code")
			}
		}
		return identity, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return pairingIdentity{}, err
	}
	var id [16]byte
	if _, err := rand.Read(id[:]); err != nil {
		return pairingIdentity{}, err
	}
	identity := pairingIdentity{ID: hex.EncodeToString(id[:]), Code: configuredPairingCode()}
	raw, err = json.Marshal(identity)
	if err != nil {
		return identity, err
	}
	// Exclusive creation avoids replacing credentials from another process.
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if errors.Is(err, os.ErrExist) {
		return loadPairingIdentity()
	}
	if err != nil {
		return identity, err
	}
	_, err = file.Write(raw)
	if err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err == nil {
		err = closeErr
	}
	if err != nil {
		_ = os.Remove(path)
	}
	return identity, err
}
