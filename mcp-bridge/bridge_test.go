package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func responseFor(raw string) []byte {
	return []byte("Result: Bundle[{payload=" + base64.StdEncoding.EncodeToString([]byte(raw)) + "}]\n")
}

func TestResponseRejectsUnexpectedOutput(t *testing.T) {
	for _, raw := range [][]byte{[]byte("error with arbitrary device output"), responseFor("{}"), responseFor(`{"ok":true} {}`), responseFor(`{"ok":"true"}`)} {
		if _, err := decodeResponse(raw); err == nil {
			t.Fatal("invalid output accepted")
		}
	}
	result, err := decodeResponse(responseFor(`{"ok":true,"data":{"text":"Türkçe\n\"quoted\""}}`))
	if err != nil || result["ok"] != true {
		t.Fatalf("valid response rejected: %v", err)
	}
}

func TestTransportHandlesSpacesInWirelessSerialAndDoesNotUseShell(t *testing.T) {
	called := false
	bridge := &adbBridge{config: config{ADB: "adb.exe", Device: "R5TEST", Token: "test-key"}}
	bridge.execute = func(ctx context.Context, path string, args ...string) ([]byte, error) {
		if args[0] == "devices" {
			return []byte("List of devices attached\nadb-R5TEST-abc (2)._adb-tls-connect._tcp device product:r9q transport_id:7\n"), nil
		}
		if args[3] == "getprop" {
			return []byte("R5TEST\n"), nil
		}
		if args[0] != "-t" || args[1] != "7" || args[3] != "content" || len(args) != 11 {
			t.Fatalf("unexpected ADB invocation: %#v", args)
		}
		raw, err := base64.StdEncoding.DecodeString(args[len(args)-1])
		if err != nil {
			t.Fatal(err)
		}
		var envelope map[string]any
		if json.Unmarshal(raw, &envelope) != nil {
			t.Fatal("invalid request JSON")
		}
		text := envelope["arguments"].(map[string]any)["search"]
		if text != "Türkçe; $(danger) ' \n" {
			t.Fatalf("text changed: %v", text)
		}
		called = true
		return responseFor(`{"ok":true,"data":{}}`), nil
	}
	_, err := bridge.call(context.Background(), "ideas_list", map[string]any{"search": "Türkçe; $(danger) ' \n"})
	if err != nil || !called {
		t.Fatalf("bridge failed: %v", err)
	}
}

func TestOversizedRequestDoesNotReachADB(t *testing.T) {
	b := &adbBridge{execute: func(context.Context, string, ...string) ([]byte, error) {
		t.Fatal("ADB called for rejected request")
		return nil, nil
	}}
	if _, err := b.call(context.Background(), "ideas_write", map[string]any{"text": strings.Repeat("x", 16000)}); err == nil {
		t.Fatal("large request accepted")
	}
}

func TestOfflineDeviceIsNotSilentlyReplaced(t *testing.T) {
	b := &adbBridge{config: config{Device: "emulator-5554"}, execute: func(ctx context.Context, path string, args ...string) ([]byte, error) {
		if args[0] == "devices" {
			return []byte("unrelated device product:r9q transport_id:4\n"), nil
		}
		if args[3] == "getprop" {
			return []byte("OtherPhone"), nil
		}
		return nil, errors.New("unexpected call")
	}}
	if _, err := b.resolve(context.Background()); err == nil {
		t.Fatal("unrelated device selected")
	}
}

func TestExplicitEmulatorNeverProbesOtherPhones(t *testing.T) {
	b := &adbBridge{config: config{Device: "emulator-5554"}, execute: func(ctx context.Context, path string, args ...string) ([]byte, error) {
		if args[0] != "devices" {
			t.Fatal("unrelated device was probed")
		}
		return []byte("some-phone device product:r9q transport_id:1\nemulator-5554 device product:sdk transport_id:2\n"), nil
	}}
	transport, err := b.resolve(context.Background())
	if err != nil || transport != "2" {
		t.Fatal("explicit emulator selection failed")
	}
}

func TestOfficialSDKHandshakeDiscoveryAndValidation(t *testing.T) {
	calls := 0
	bridge := &adbBridge{config: config{Device: "emulator-5554"}, execute: func(ctx context.Context, path string, args ...string) ([]byte, error) {
		if args[0] == "devices" {
			return []byte("emulator-5554 device product:sdk transport_id:1\n"), nil
		}
		calls++
		return responseFor(`{"ok":true,"data":{"apiVersion":1}}`), nil
	}}
	server, err := newServer(bridge)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	serverTransport, clientTransport := mcp.NewInMemoryTransports()
	ss, err := server.Connect(ctx, serverTransport, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer ss.Close()
	client := mcp.NewClient(&mcp.Implementation{Name: "test", Version: "1"}, nil)
	session, err := client.Connect(ctx, clientTransport, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	listed, err := session.ListTools(ctx, nil)
	if err != nil || len(listed.Tools) != 17 {
		t.Fatalf("unexpected tools: %v", err)
	}
	result, err := session.CallTool(ctx, &mcp.CallToolParams{Name: "app_status", Arguments: map[string]any{}})
	if err != nil || result.IsError || calls != 1 {
		t.Fatalf("call failed: %v", err)
	}
	result, err = session.CallTool(ctx, &mcp.CallToolParams{Name: "ideas_list", Arguments: map[string]any{"unexpected": "field"}})
	if err == nil && !result.IsError {
		t.Fatal("unknown property accepted")
	}
	if calls != 1 {
		t.Fatal("invalid arguments reached device")
	}
	result, err = session.CallTool(ctx, &mcp.CallToolParams{Name: "ideas_write", Arguments: map[string]any{"action": "create", "text": "Work"}})
	if err == nil && !result.IsError {
		t.Fatal("missing requestId accepted")
	}
	if calls != 1 {
		t.Fatal("invalid mutation reached device")
	}
}
