//go:build integration

package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func TestEmulatorStdioSmoke(t *testing.T) {
	path := os.Getenv("GUNLUK_MCP_TEST_CONFIG")
	if path == "" {
		t.Skip("set GUNLUK_MCP_TEST_CONFIG to the emulator-only config")
	}
	c, err := loadConfig(path)
	if err != nil || !strings.HasPrefix(c.Device, "emulator-") {
		t.Fatal("physical devices and invalid configs are refused")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	executable, err := filepath.Abs("gunluk-takip-mcp.exe")
	if err != nil {
		t.Fatal(err)
	}
	client := mcp.NewClient(&mcp.Implementation{Name: "stdio-smoke", Version: "1"}, nil)
	session, err := client.Connect(ctx, &mcp.CommandTransport{Command: exec.CommandContext(ctx, executable, "--config", path)}, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	call := func(name string, args map[string]any) map[string]any {
		t.Helper()
		result, err := session.CallTool(ctx, &mcp.CallToolParams{Name: name, Arguments: args})
		if err != nil || result.IsError {
			t.Fatalf("%s failed: %v", name, err)
		}
		raw, _ := json.Marshal(result.StructuredContent)
		var envelope map[string]any
		if json.Unmarshal(raw, &envelope) != nil || envelope["ok"] != true {
			t.Fatalf("%s returned an error", name)
		}
		return envelope["data"].(map[string]any)
	}
	listed, err := session.ListTools(ctx, nil)
	if err != nil || len(listed.Tools) != 17 {
		t.Fatal("stdio discovery failed")
	}
	call("app_status", map[string]any{})
	call("settings_get", map[string]any{})
	call("sleep_list", map[string]any{"limit": 1})
	if mode := os.Getenv("GUNLUK_MCP_UI_MARKER"); mode != "" {
		const text = "QA MCP visible record"
		rows := call("agenda_list", map[string]any{"allDates": true, "search": text, "limit": 100})["items"].([]any)
		if mode == "create" && len(rows) == 0 {
			call("agenda_write", map[string]any{"requestId": fmt.Sprintf("ui-marker-%d", time.Now().UnixNano()), "action": "create", "text": text, "category": "MCP Test"})
		} else if mode == "cleanup" {
			for _, raw := range rows {
				row := raw.(map[string]any)
				if row["text"] != text || row["category"] != "MCP Test" {
					t.Fatal("refusing to delete an unrelated fixture")
				}
				preview := call("record_delete_preview", map[string]any{"kind": "agenda", "id": row["id"]})
				call("record_delete", map[string]any{"requestId": fmt.Sprintf("ui-cleanup-%d", time.Now().UnixNano()), "kind": "agenda", "id": row["id"], "expectedRevision": row["revision"], "confirmation": preview["confirmation"]})
			}
		}
	}
}
