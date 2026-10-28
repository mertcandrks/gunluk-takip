//go:build integration

package main

import (
	"context"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"
)

func TestEmulatorPermissionPolicies(t *testing.T) {
	expected := os.Getenv("GUNLUK_MCP_EXPECT_DENIAL")
	if expected == "" {
		t.Skip("set GUNLUK_MCP_EXPECT_DENIAL after changing access in emulator UI")
	}
	c, err := loadConfig(os.Getenv("GUNLUK_MCP_TEST_CONFIG"))
	if err != nil || !strings.HasPrefix(c.Device, "emulator-") {
		t.Fatal("an emulator-only configuration is required")
	}
	bridge, err := newADBBridge(c)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
	defer cancel()
	status, err := bridge.call(ctx, "app_status", map[string]any{})
	if err != nil || status["ok"] != true {
		t.Fatal("status failed")
	}
	access := status["data"].(map[string]any)
	args := map[string]any{}
	tool := "ideas_list"
	switch expected {
	case "DELETE_DISABLED":
		if access["writes"] != true || access["deletes"] != false {
			t.Fatal("configure writes ON and deletion OFF in emulator UI first")
		}
		tool = "record_delete"
		args = map[string]any{"requestId": fmt.Sprintf("policy-%d", time.Now().UnixNano()), "kind": "agenda", "id": 0, "expectedRevision": strings.Repeat("0", 64), "confirmation": strings.Repeat("0", 64)}
	case "WRITE_DISABLED":
		if access["enabled"] != true || access["writes"] != false {
			t.Fatal("configure MCP ON and writes OFF first")
		}
		tool = "agenda_write"
		args = map[string]any{"requestId": fmt.Sprintf("policy-%d", time.Now().UnixNano()), "action": "create", "day": "2099-01-02", "text": "QA policy denial"}
	case "UNAUTHORIZED":
		if access["enabled"] != false {
			t.Fatal("disable MCP in emulator UI first")
		}
	default:
		t.Fatal("unsupported policy check")
	}
	result, err := bridge.call(ctx, tool, args)
	if err != nil || result["ok"] != false || result["error"].(map[string]any)["code"] != expected {
		t.Fatalf("policy was not enforced: expected %s", expected)
	}
}
