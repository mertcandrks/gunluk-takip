package main

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
)

//go:embed contract.json
var assets embed.FS

type toolSpec struct {
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Arguments   []string `json:"arguments"`
	Required    []string `json:"required"`
	Actions     []string `json:"actions"`
	Kinds       []string `json:"kinds"`
	Write       bool     `json:"write"`
	Private     bool     `json:"private"`
	Destructive bool     `json:"destructive"`
}
type contract struct {
	Version    int                       `json:"version"`
	Properties map[string]map[string]any `json:"properties"`
	Tools      []toolSpec                `json:"tools"`
}
type config struct {
	ADB    string `json:"adb"`
	Device string `json:"device"`
	Token  string `json:"token"`
}

func defaultConfigPath() string {
	root, err := os.UserConfigDir()
	if err != nil {
		root = "."
	}
	return filepath.Join(root, "GunlukTakipMcp", "config.json")
}

func loadConfig(path string) (config, error) {
	file, err := os.Open(path)
	if err != nil {
		return config{}, errors.New("configuration missing; open AI / MCP access in the app and run --pair --device DEVICE")
	}
	defer file.Close()
	var c config
	decoder := json.NewDecoder(io.LimitReader(file, 8193))
	decoder.DisallowUnknownFields()
	if decoder.Decode(&c) != nil || decoder.Decode(new(any)) != io.EOF {
		return c, errors.New("invalid configuration")
	}
	return c, nil
}

func schemaFor(c contract, spec toolSpec) map[string]any {
	properties := map[string]any{}
	for _, name := range spec.Arguments {
		field := map[string]any{}
		for key, value := range c.Properties[name] {
			field[key] = value
		}
		if name == "action" && len(spec.Actions) > 0 {
			field["enum"] = spec.Actions
		}
		if name == "kind" && len(spec.Kinds) > 0 {
			field["enum"] = spec.Kinds
		}
		properties[name] = field
	}
	required := append([]string{}, spec.Required...)
	if spec.Write {
		required = append(required, "requestId")
	}
	return map[string]any{"type": "object", "properties": properties, "required": required, "additionalProperties": false}
}

func newServer(bridge *adbBridge) (*mcp.Server, error) {
	raw, err := assets.ReadFile("contract.json")
	if err != nil {
		return nil, err
	}
	var c contract
	if err = json.Unmarshal(raw, &c); err != nil {
		return nil, err
	}
	server := mcp.NewServer(&mcp.Implementation{Name: "gunluk-takip", Version: "1.0.0"}, nil)
	for _, spec := range c.Tools {
		destructive := spec.Destructive || spec.Name == "agenda_write"
		closed := false
		mcp.AddTool(server, &mcp.Tool{
			Name: spec.Name, Description: spec.Description, InputSchema: schemaFor(c, spec),
			Annotations: &mcp.ToolAnnotations{ReadOnlyHint: !spec.Write, IdempotentHint: spec.Write, DestructiveHint: &destructive, OpenWorldHint: &closed},
		}, func(ctx context.Context, request *mcp.CallToolRequest, args map[string]any) (*mcp.CallToolResult, map[string]any, error) {
			result, err := bridge.call(ctx, spec.Name, args)
			if err != nil {
				result = map[string]any{"ok": false, "error": map[string]any{"code": "BRIDGE_UNAVAILABLE", "message": err.Error()}}
			}
			ok, _ := result["ok"].(bool)
			return &mcp.CallToolResult{IsError: !ok}, result, nil
		})
	}
	return server, nil
}

func run() error {
	path := flag.String("config", defaultConfigPath(), "Private credential file")
	device := flag.String("device", "", "ADB serial or hardware serial; required when pairing")
	adb := flag.String("adb", "", "Absolute adb.exe path (optional)")
	pair := flag.Bool("pair", false, "Consume the app's one-minute pairing window and save credentials")
	flag.Parse()
	c, err := loadConfig(*path)
	if err != nil && !*pair {
		return err
	}
	if *device != "" {
		c.Device = *device
	}
	if *adb != "" {
		c.ADB = *adb
	}
	if c.Device == "" {
		return errors.New("select a device explicitly")
	}
	bridge, err := newADBBridge(c)
	if err != nil {
		return err
	}
	if *pair {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		response, err := bridge.call(ctx, "pair_access", map[string]any{})
		if err != nil {
			return err
		}
		data, _ := response["data"].(map[string]any)
		token, _ := data["token"].(string)
		if len(token) != 43 {
			return errors.New("pairing closed; enable MCP and open PC pairing in the app")
		}
		c.ADB = bridge.config.ADB
		c.Token = token
		encoded, err := json.MarshalIndent(c, "", "  ")
		if err != nil {
			return err
		}
		if err = os.MkdirAll(filepath.Dir(*path), 0700); err != nil {
			return errors.New("cannot create private config directory")
		}
		tmp, err := os.CreateTemp(filepath.Dir(*path), ".pair-*")
		if err != nil {
			return errors.New("cannot save credentials")
		}
		name := tmp.Name()
		defer os.Remove(name)
		if err = protectCredentialFile(name); err == nil {
			_, err = tmp.Write(encoded)
		}
		closeErr := tmp.Close()
		if err != nil || closeErr != nil {
			return errors.New("cannot save credentials")
		}
		if err = os.Rename(name, *path); err != nil {
			return errors.New("cannot replace configuration")
		}
		fmt.Fprintln(os.Stderr, "Paired. Private configuration saved; no key was printed.")
		return nil
	}
	server, err := newServer(bridge)
	if err != nil {
		return err
	}
	return server.Run(context.Background(), &mcp.StdioTransport{})
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
