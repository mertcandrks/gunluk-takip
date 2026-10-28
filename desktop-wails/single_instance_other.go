//go:build !windows

package main

func acquireSingleInstance() (release func(), acquired bool) {
	return func() {}, true
}
