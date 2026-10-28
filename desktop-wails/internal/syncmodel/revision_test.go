package syncmodel

import (
	"fmt"
	"reflect"
	"testing"
	"testing/quick"
)

func testID(n int) string { return fmt.Sprintf("00000000-0000-4000-8000-%012d", n) }
func rev(n int, payload string, deleted bool, parents ...string) Revision {
	return Revision{ID: testID(n), RecordID: testID(100), DeviceID: testID(200), Kind: "agenda", Parents: parents, Payload: payload, Deleted: deleted}
}

func TestConcurrentEditDeleteAndResolution(t *testing.T) {
	base := rev(1, "original", false)
	edit := rev(2, "edited", false, base.ID)
	del := rev(3, "", true, base.ID)
	graph, err := Merge(nil, []Revision{del, base, edit})
	if err != nil {
		t.Fatal(err)
	}
	if len(Heads(graph, base.RecordID)) != 2 {
		t.Fatal("conflict was lost")
	}
	resolved := rev(4, "keep edit", false, edit.ID, del.ID)
	merged, err := Merge(graph, []Revision{resolved})
	if err != nil {
		t.Fatal(err)
	}
	if got := Heads(merged, base.RecordID); len(got) != 1 || got[0].ID != resolved.ID {
		t.Fatal(got)
	}
	if len(Heads(graph, base.RecordID)) != 2 {
		t.Fatal("input mutated")
	}
}

func TestStaleReplayCannotResurrect(t *testing.T) {
	base := rev(1, "original", false)
	del := rev(2, "", true, base.ID)
	graph, err := Merge([]Revision{base, del}, []Revision{base})
	if err != nil {
		t.Fatal(err)
	}
	heads := Heads(graph, base.RecordID)
	if len(heads) != 1 || !heads[0].Deleted {
		t.Fatal(heads)
	}
}

func TestRejectInvalidGraph(t *testing.T) {
	cases := [][]Revision{
		{rev(1, "a", false), rev(1, "b", false)},
		{rev(1, "", false, testID(2))},
		{rev(1, "", false, testID(2)), rev(2, "", false, testID(1))},
		{rev(1, "not empty", true)},
		{rev(1, "", false, testID(1))},
		{rev(1, "", false, testID(2), testID(2))},
	}
	for i, input := range cases {
		if _, err := Merge(nil, input); err == nil {
			t.Fatalf("accepted invalid case %d", i)
		}
	}
	other := rev(2, "", false, testID(1))
	other.RecordID = testID(101)
	if _, err := Merge(nil, []Revision{rev(1, "", false), other}); err == nil {
		t.Fatal("accepted unrelated parent")
	}
	other = rev(2, "", false)
	other.Kind = "sleep"
	if _, err := Merge(nil, []Revision{rev(1, "", false), other}); err == nil {
		t.Fatal("accepted kind change")
	}
}

func TestMergeAlgebra(t *testing.T) {
	property := func(leftText, rightText string, deleted bool) bool {
		base := rev(1, "base", false)
		if deleted {
			rightText = ""
		}
		left := []Revision{base, rev(2, leftText, false, base.ID)}
		right := []Revision{base, rev(3, rightText, deleted, base.ID)}
		lr, e1 := Merge(left, right)
		rl, e2 := Merge(right, left)
		replay, e3 := Merge(lr, lr)
		return e1 == nil && e2 == nil && e3 == nil && reflect.DeepEqual(lr, rl) && reflect.DeepEqual(lr, replay) && len(Heads(lr, base.RecordID)) == 2
	}
	if err := quick.Check(property, &quick.Config{MaxCount: 500}); err != nil {
		t.Fatal(err)
	}
}
