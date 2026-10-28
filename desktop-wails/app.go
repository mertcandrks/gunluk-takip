package main

import (
	"context"
	"sync"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

type App struct {
	ctx              context.Context
	mu               sync.RWMutex
	server           *SyncServer
	snapshot         *Snapshot
	status           string
	stopAutomation   context.CancelFunc
	automationStatus string
}

type AppState struct {
	PairingCode      string    `json:"pairingCode"`
	Addresses        []string  `json:"addresses"`
	Status           string    `json:"status"`
	Snapshot         *Snapshot `json:"snapshot,omitempty"`
	ServiceID        string    `json:"serviceId"`
	AutomationStatus string    `json:"automationStatus"`
}

func NewApp() *App { return &App{status: "Telefon bekleniyor"} }

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	if cached, err := loadSnapshot(); err == nil {
		a.snapshot = cached
	} else {
		a.snapshot = &Snapshot{GeneratedAtMillis: time.Now().UnixMilli(), WaterGoalMl: 2500}
	}
	a.server = NewSyncServer(a.onSnapshot, a.onStatus)
	go a.server.Run()
	automationContext, cancel := context.WithCancel(ctx)
	a.stopAutomation = cancel
	go a.runAutomationInbox(automationContext)
}

func (a *App) shutdown(context.Context) {
	if a.stopAutomation != nil {
		a.stopAutomation()
	}
	if a.server != nil {
		a.server.Close()
	}
}

func (a *App) GetState() AppState {
	a.mu.RLock()
	defer a.mu.RUnlock()
	code := "--------"
	if a.server != nil {
		code = a.server.PairingCode()
	}
	id := ""
	if a.server != nil {
		id = a.server.ServiceID()
	}
	return AppState{PairingCode: code, Addresses: localAddresses(), Status: a.status, Snapshot: a.snapshot, ServiceID: id, AutomationStatus: a.automationStatus}
}

func (a *App) onSnapshot(snapshot *Snapshot, encoded []byte) ([]byte, error) {
	a.mu.Lock()
	local := a.snapshot
	merged := mergeSnapshots(local, snapshot)
	mergedBytes, err := encodeSnapshot(merged)
	if err != nil {
		a.mu.Unlock()
		return nil, err
	}
	if err = saveSnapshot(mergedBytes); err != nil {
		a.mu.Unlock()
		return nil, err
	}
	a.snapshot = merged
	a.mu.Unlock()
	if a.ctx != nil {
		runtime.EventsEmit(a.ctx, "sync:updated", a.GetState())
	}
	return mergedBytes, nil
}

func (a *App) onStatus(status string) {
	a.mu.Lock()
	a.status = status
	a.mu.Unlock()
	if a.ctx != nil {
		runtime.EventsEmit(a.ctx, "sync:status", a.GetState())
	}
}
