//go:build windows

package main

import (
	"errors"
	"os"
	"os/exec"
	"os/user"
	"path/filepath"
	"syscall"
)

func hideWindow(cmd *exec.Cmd) { cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true} }

func protectCredentialFile(path string) error {
	current, err := user.Current()
	if err != nil {
		return errors.New("cannot identify credential owner")
	}
	exe := filepath.Join(os.Getenv("SystemRoot"), "System32", "icacls.exe")
	cmd := exec.Command(exe, path, "/inheritance:r", "/grant:r", "*"+current.Uid+":F", "*S-1-5-18:F")
	hideWindow(cmd)
	if cmd.Run() != nil {
		return errors.New("cannot protect credentials with a user-only Windows ACL")
	}
	return nil
}
