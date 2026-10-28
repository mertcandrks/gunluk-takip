//go:build windows

package main

import "golang.org/x/sys/windows"

func acquireSingleInstance() (release func(), acquired bool) {
	name, err := windows.UTF16PtrFromString(`Local\GunlukTakipPC-SingleInstance`)
	if err != nil {
		return func() {}, false
	}
	handle, err := windows.CreateMutex(nil, true, name)
	if err == windows.ERROR_ALREADY_EXISTS {
		_ = windows.CloseHandle(handle)
		return func() {}, false
	}
	if err != nil {
		return func() {}, false
	}
	return func() {
		_ = windows.ReleaseMutex(handle)
		_ = windows.CloseHandle(handle)
	}, true
}
