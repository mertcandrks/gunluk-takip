package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"
)

type adbBridge struct {
	config  config
	mu      sync.Mutex
	execute func(context.Context, string, ...string) ([]byte, error)
}

func newADBBridge(c config) (*adbBridge, error) {
	if c.ADB == "" {
		c.ADB = filepath.Join(os.Getenv("LOCALAPPDATA"), "Android", "Sdk", "platform-tools", "adb.exe")
		if _, err := os.Stat(c.ADB); err != nil {
			path, err := exec.LookPath("adb")
			if err != nil {
				return nil, errors.New("ADB not found; configure its absolute path")
			}
			c.ADB = path
		}
	}
	if !filepath.IsAbs(c.ADB) {
		return nil, errors.New("ADB path must be absolute")
	}
	if len(c.Device) > 200 || strings.ContainsAny(c.Device, "\r\n\x00") {
		return nil, errors.New("invalid device identity")
	}
	return &adbBridge{config: c, execute: runADB}, nil
}

type boundedBuffer struct {
	bytes.Buffer
	overflow bool
}

func (b *boundedBuffer) Write(p []byte) (int, error) {
	if b.Len()+len(p) > 300000 {
		b.overflow = true
		return 0, errors.New("output limit exceeded")
	}
	return b.Buffer.Write(p)
}

func runADB(ctx context.Context, path string, args ...string) ([]byte, error) {
	cmd := exec.CommandContext(ctx, path, args...)
	hideWindow(cmd)
	var output boundedBuffer
	cmd.Stdout = &output
	cmd.Stderr = io.Discard
	if err := cmd.Run(); err != nil || output.overflow {
		return nil, errors.New("ADB command failed")
	}
	return output.Bytes(), nil
}

var deviceLine = regexp.MustCompile(`(?m)^(.+?)\s+device\s+.*transport_id:([0-9]+)\s*$`)

func (b *adbBridge) resolve(ctx context.Context) (string, error) {
	raw, err := b.execute(ctx, b.config.ADB, "devices", "-l")
	if err != nil {
		return "", errors.New("cannot discover ADB devices")
	}
	matches := deviceLine.FindAllStringSubmatch(string(raw), -1)
	for _, match := range matches {
		serial := strings.TrimSpace(match[1])
		if serial == b.config.Device {
			return match[2], nil
		}
	}
	if strings.HasPrefix(b.config.Device, "emulator-") || strings.Contains(b.config.Device, ":") {
		return "", errors.New("paired device is offline or unauthorized")
	}
	for _, match := range matches {
		identity, err := b.execute(ctx, b.config.ADB, "-t", match[2], "shell", "getprop", "ro.serialno")
		if err == nil && strings.TrimSpace(string(identity)) == b.config.Device {
			return match[2], nil
		}
	}
	return "", errors.New("paired device is offline or unauthorized")
}

func (b *adbBridge) reconnect(ctx context.Context) {
	if strings.ContainsAny(b.config.Device, ":.") || strings.HasPrefix(b.config.Device, "emulator-") {
		return
	}
	raw, err := b.execute(ctx, b.config.ADB, "mdns", "services")
	if err != nil {
		return
	}
	for _, line := range strings.Split(string(raw), "\n") {
		fields := strings.Fields(line)
		if len(fields) < 3 || !strings.HasPrefix(fields[0], "adb-"+b.config.Device+"-") || !strings.Contains(line, "_adb-tls-connect._tcp") {
			continue
		}
		endpoint := fields[len(fields)-1]
		host, _, err := net.SplitHostPort(endpoint)
		if err != nil || net.ParseIP(host) == nil {
			continue
		}
		_, _ = b.execute(ctx, b.config.ADB, "connect", endpoint)
	}
}

func (b *adbBridge) call(ctx context.Context, tool string, args map[string]any) (map[string]any, error) {
	raw, err := json.Marshal(map[string]any{"tool": tool, "arguments": args, "token": b.config.Token})
	if err != nil || len(raw) > 16000 {
		return nil, errors.New("request too large; split the batch or shorten text")
	}
	ctx, cancel := context.WithTimeout(ctx, 25*time.Second)
	defer cancel()
	b.mu.Lock()
	defer b.mu.Unlock()
	transport, err := b.resolve(ctx)
	if err != nil {
		b.reconnect(ctx)
		transport, err = b.resolve(ctx)
	}
	if err != nil {
		return nil, err
	}
	output, err := b.execute(ctx, b.config.ADB, "-t", transport, "shell", "content", "call",
		"--uri", "content://com.uykutakip.app.mcp", "--method", "rpc", "--arg", base64.StdEncoding.EncodeToString(raw))
	if err != nil {
		return nil, errors.New("device call failed; retry with the same requestId")
	}
	return decodeResponse(output)
}

var responsePattern = regexp.MustCompile(`^Result: Bundle\[\{payload=([A-Za-z0-9+/=]+)\}\]\s*$`)

func decodeResponse(output []byte) (map[string]any, error) {
	match := responsePattern.FindSubmatch(bytes.TrimSpace(output))
	if len(match) != 2 {
		return nil, errors.New("invalid app response; check the app version and provider")
	}
	raw, err := base64.StdEncoding.DecodeString(string(match[1]))
	if err != nil || len(raw) > 128000 {
		return nil, errors.New("invalid app response size")
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	var result map[string]any
	if decoder.Decode(&result) != nil || decoder.Decode(new(any)) != io.EOF {
		return nil, errors.New("invalid app JSON response")
	}
	if _, ok := result["ok"].(bool); !ok {
		return nil, errors.New("invalid response envelope")
	}
	return result, nil
}
