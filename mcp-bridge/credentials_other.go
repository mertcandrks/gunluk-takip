//go:build !windows

package main

import (
	"os"
	"os/exec"
)

func hideWindow(cmd *exec.Cmd)                {}
func protectCredentialFile(path string) error { return os.Chmod(path, 0600) }
