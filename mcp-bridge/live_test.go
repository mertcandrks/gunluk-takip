//go:build integration

package main

import (
	"context"
	"crypto/rand"
	"encoding/base64"
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

// This suite writes synthetic records and access preferences on an emulator only.
func TestEmulatorRoundTrip(t *testing.T) {
	device := os.Getenv("GUNLUK_MCP_TEST_DEVICE")
	if !strings.HasPrefix(device, "emulator-") {
		t.Skip("set GUNLUK_MCP_TEST_DEVICE to an emulator serial; physical devices are refused")
	}
	tokenBytes := make([]byte, 32)
	if _, err := rand.Read(tokenBytes); err != nil {
		t.Fatal(err)
	}
	token := base64.RawURLEncoding.EncodeToString(tokenBytes)
	bridge, err := newADBBridge(config{Device: device, Token: token})
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 180*time.Second)
	defer cancel()
	if _, err = runADB(ctx, bridge.config.ADB, "-s", device, "shell", "am", "force-stop", "com.uykutakip.app"); err != nil {
		t.Fatal(err)
	}
	preferences := fmt.Sprintf(`<?xml version="1.0" encoding="utf-8"?><map><boolean name="enabled" value="true"/><boolean name="writes" value="true"/><boolean name="deletes" value="true"/><boolean name="private" value="false"/><string name="token">%s</string></map>`, token)
	setup := exec.CommandContext(ctx, bridge.config.ADB, "-s", device, "shell", "run-as com.uykutakip.app sh -c 'mkdir -p shared_prefs; cat > shared_prefs/mcp_access.xml'")
	setup.Stdin = strings.NewReader(preferences)
	if output, err := setup.CombinedOutput(); err != nil {
		t.Fatalf("emulator-only setup failed: %v: %s", err, output)
	}
	if _, err = runADB(ctx, bridge.config.ADB, "-s", device, "shell", "am", "start", "-n", "com.uykutakip.app/.MainActivity"); err != nil {
		t.Fatal(err)
	}

	server, err := newServer(bridge)
	if err != nil {
		t.Fatal(err)
	}
	st, ct := mcp.NewInMemoryTransports()
	ss, err := server.Connect(ctx, st, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer ss.Close()
	session, err := mcp.NewClient(&mcp.Implementation{Name: "emulator-e2e", Version: "1"}, nil).Connect(ctx, ct, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	call := func(name string, args map[string]any) map[string]any {
		t.Helper()
		result, err := session.CallTool(ctx, &mcp.CallToolParams{Name: name, Arguments: args})
		if err != nil {
			t.Fatalf("%s transport failed: %v", name, err)
		}
		raw, err := json.Marshal(result.StructuredContent)
		if err != nil {
			t.Fatal(err)
		}
		var envelope map[string]any
		if json.Unmarshal(raw, &envelope) != nil {
			t.Fatalf("%s has invalid structured result", name)
		}
		return envelope
	}
	okData := func(value map[string]any) map[string]any {
		t.Helper()
		if value["ok"] != true {
			t.Fatalf("tool failed: %#v", value["error"])
		}
		return value["data"].(map[string]any)
	}
	suffix := fmt.Sprintf("mcp-test-%d", time.Now().UnixNano())
	request := func(action string) string { return suffix + "-" + action }
	status := okData(call("app_status", map[string]any{}))
	if status["apiVersion"] != float64(1) || status["enabled"] != true {
		t.Fatal("app status not ready")
	}
	denied := call("private_list", map[string]any{})
	if denied["ok"] != false || denied["error"].(map[string]any)["code"] != "PRIVATE_DISABLED" {
		t.Fatal("private access leaked")
	}
	wrong := adbBridge{config: bridge.config, execute: bridge.execute}
	wrong.config.Token = strings.Repeat("x", 43)
	wrongResult, err := wrong.call(ctx, "ideas_list", map[string]any{})
	if err != nil || wrongResult["ok"] != false || wrongResult["error"].(map[string]any)["code"] != "UNAUTHORIZED" {
		t.Fatal("bad token was accepted")
	}

	createArgs := map[string]any{"requestId": request("create"), "action": "create", "day": "2099-01-02", "text": suffix + " Türkçe\nquoted ' ; $(not-a-command)", "category": "MCP Test"}
	created := okData(call("agenda_write", createArgs))["created"].([]any)[0].(map[string]any)
	if _, err := runADB(ctx, bridge.config.ADB, "-s", device, "shell", "am", "force-stop", "com.uykutakip.app"); err != nil {
		t.Fatal(err)
	}
	if _, err := runADB(ctx, bridge.config.ADB, "-s", device, "shell", "am", "start", "-n", "com.uykutakip.app/.MainActivity"); err != nil {
		t.Fatal(err)
	}
	repeated := okData(call("agenda_write", createArgs))["created"].([]any)[0].(map[string]any)
	if created["id"] != repeated["id"] {
		t.Fatal("retry duplicated a record")
	}
	id := created["id"]
	collisionArgs := map[string]any{}
	for key, value := range createArgs {
		collisionArgs[key] = value
	}
	collisionArgs["text"] = suffix + " must-not-create"
	collision := call("agenda_write", collisionArgs)
	if collision["ok"] != false || collision["error"].(map[string]any)["code"] != "REQUEST_ID_CONFLICT" {
		t.Fatal("request ID collision accepted")
	}
	invalidBatch := map[string]any{"requestId": request("batch"), "action": "create_batch", "day": "2099-01-02", "tasks": []any{map[string]any{"text": suffix + " first"}, map[string]any{"text": " "}}}
	invalid := call("agenda_write", invalidBatch)
	if invalid["ok"] != false {
		t.Fatal("invalid batch accepted")
	}
	invalidBatch["tasks"] = []any{map[string]any{"text": suffix + " first"}, map[string]any{"text": suffix + " second"}}
	okData(call("agenda_write", invalidBatch))
	revised := okData(call("agenda_write", map[string]any{"requestId": request("edit"), "action": "edit", "id": id, "text": suffix + " updated", "expectedRevision": created["revision"]}))
	conflict := call("agenda_write", map[string]any{"requestId": request("stale"), "action": "edit", "id": id, "text": "must-not-overwrite", "expectedRevision": created["revision"]})
	if conflict["ok"] != false || conflict["error"].(map[string]any)["code"] != "REVISION_CONFLICT" {
		t.Fatal("stale revision overwrote record")
	}
	running := okData(call("agenda_write", map[string]any{"requestId": request("start"), "action": "timer", "id": id, "running": true, "expectedRevision": revised["revision"]}))
	if running["timerStartedAtMillis"].(float64) <= 0 {
		t.Fatal("timer did not start")
	}
	stopped := okData(call("agenda_write", map[string]any{"requestId": request("pause"), "action": "timer", "id": id, "running": false, "expectedRevision": running["revision"]}))
	if stopped["timerStartedAtMillis"].(float64) != 0 {
		t.Fatal("timer did not pause")
	}
	shop := okData(call("shopping_write", map[string]any{"requestId": request("shop"), "action": "create", "text": suffix + " soap", "longTerm": false}))
	shop = okData(call("shopping_write", map[string]any{"requestId": request("purchase"), "action": "set_purchased", "id": shop["id"], "purchased": true, "expectedRevision": shop["revision"]}))
	if shop["purchased"] != true {
		t.Fatal("shopping state not updated")
	}
	idea := okData(call("ideas_write", map[string]any{"requestId": request("idea"), "action": "create", "text": suffix + " idea"}))
	okData(call("ideas_write", map[string]any{"requestId": request("idea-finish"), "action": "finish", "id": idea["id"], "expectedRevision": idea["revision"]}))
	entries := okData(call("agenda_list", map[string]any{"allDates": true, "search": suffix, "limit": 100}))["items"].([]any)
	if len(entries) != 4 {
		t.Fatalf("expected original task, batch and completed idea, found %d", len(entries))
	}
	for index, row := range entries {
		entity := row.(map[string]any)
		preview := okData(call("record_delete_preview", map[string]any{"kind": "agenda", "id": entity["id"]}))
		okData(call("record_delete", map[string]any{"requestId": request(fmt.Sprintf("delete-agenda-%d", index)), "kind": "agenda", "id": entity["id"], "expectedRevision": entity["revision"], "confirmation": preview["confirmation"]}))
	}
	for kind, entity := range map[string]map[string]any{"shopping": shop, "idea": okData(call("ideas_list", map[string]any{"search": suffix}))["items"].([]any)[0].(map[string]any)} {
		preview := okData(call("record_delete_preview", map[string]any{"kind": kind, "id": entity["id"]}))
		okData(call("record_delete", map[string]any{"requestId": request("delete-" + kind), "kind": kind, "id": entity["id"], "expectedRevision": entity["revision"], "confirmation": preview["confirmation"]}))
	}
	stats := okData(call("statistics_get", map[string]any{"from": "2099-01-02", "to": "2099-01-02"}))
	if stats["tasks"] != float64(0) {
		t.Fatal("synthetic task cleanup failed")
	}
	// Save emulator credentials outside the repository for an optimized-build smoke check.
	if path := os.Getenv("GUNLUK_MCP_TEST_CONFIG"); path != "" {
		if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
			t.Fatal(err)
		}
		raw, _ := json.Marshal(bridge.config)
		if err := os.WriteFile(path, raw, 0600); err != nil {
			t.Fatal(err)
		}
	}
}
