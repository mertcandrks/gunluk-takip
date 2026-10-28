package main

import (
	"encoding/binary"
	"net"
	"testing"
)

func TestSyncServerReturnsEncryptedMergedSnapshot(t *testing.T) {
	const code = "12345678"
	serverSide, clientSide := net.Pipe()
	defer clientSide.Close()

	server := &SyncServer{
		code: code,
		onSnapshot: func(remote *Snapshot, _ []byte) ([]byte, error) {
			return encodeSnapshot(mergeSnapshots(
				&Snapshot{Water: []WaterRecord{{ID: -1, AmountMl: 250, UpdatedAtMillis: 100}}},
				remote,
			))
		},
		onStatus: func(string) {},
	}
	serverDone := make(chan error, 1)
	go func() { serverDone <- server.receive(serverSide) }()

	remoteBytes, err := encodeSnapshot(&Snapshot{
		Water: []WaterRecord{{ID: 2, AmountMl: 125, UpdatedAtMillis: 200}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if err = writeSecurePacket(clientSide, remoteBytes, code); err != nil {
		t.Fatal(err)
	}
	var ack uint32
	if err = binary.Read(clientSide, binary.BigEndian, &ack); err != nil || ack != ackMagic {
		t.Fatalf("invalid acknowledgement: %x, %v", ack, err)
	}
	mergedBytes, err := readSecurePacket(clientSide, code)
	if err != nil {
		t.Fatal(err)
	}
	merged, err := decodeSnapshot(mergedBytes)
	if err != nil {
		t.Fatal(err)
	}
	if len(merged.Water) != 2 {
		t.Fatalf("expected records from both devices, got %#v", merged.Water)
	}
	if err = <-serverDone; err != nil {
		t.Fatal(err)
	}
	if server.PairingCode() != code {
		t.Fatal("pairing code changed during the active desktop session")
	}
}
