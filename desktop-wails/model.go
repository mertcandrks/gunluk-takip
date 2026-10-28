package main

type Snapshot struct {
	GeneratedAtMillis          int64                          `json:"generatedAtMillis"`
	ActiveSleepStartMillis     *int64                         `json:"activeSleepStartMillis,omitempty"`
	ActiveSleepUpdatedAtMillis int64                          `json:"activeSleepUpdatedAtMillis"`
	Sleeps                     []SleepRecord                  `json:"sleeps"`
	ActiveMeal                 *MealRecord                    `json:"activeMeal,omitempty"`
	Meals                      []MealRecord                   `json:"meals"`
	Water                      []WaterRecord                  `json:"water"`
	WaterGoalMl                int32                          `json:"waterGoalMl"`
	WaterGoalUpdatedAtMillis   int64                          `json:"waterGoalUpdatedAtMillis"`
	Agenda                     []AgendaRecord                 `json:"agenda"`
	AgendaTemplates            []AgendaTemplateRecord         `json:"agendaTemplates"`
	AgendaCategories           []AgendaCategoryRecord         `json:"agendaCategories"`
	HiddenAgendaSuggestions    []HiddenAgendaSuggestionRecord `json:"hiddenAgendaSuggestions"`
	PrivateActivities          []PrivateActivity              `json:"privateActivities"`
	Deletions                  []DeletionRecord               `json:"deletions"`
}

type SleepRecord struct {
	ID              int64 `json:"id"`
	StartMillis     int64 `json:"startMillis"`
	EndMillis       int64 `json:"endMillis"`
	UpdatedAtMillis int64 `json:"updatedAtMillis"`
}
type MealRecord struct {
	ID              int64  `json:"id"`
	StartMillis     int64  `json:"startMillis"`
	EndMillis       int64  `json:"endMillis"`
	UpdatedAtMillis int64  `json:"updatedAtMillis"`
	Type            string `json:"type"`
	ItemsJSON       string `json:"itemsJson"`
	Quick           bool   `json:"quick"`
}
type WaterRecord struct {
	ID              int64 `json:"id"`
	TimestampMillis int64 `json:"timestampMillis"`
	AmountMl        int32 `json:"amountMl"`
	UpdatedAtMillis int64 `json:"updatedAtMillis"`
}
type AgendaRecord struct {
	ID               int64   `json:"id"`
	Day              string  `json:"day"`
	Text             string  `json:"text"`
	Task             bool    `json:"task"`
	Completed        bool    `json:"completed"`
	NotDone          bool    `json:"notDone"`
	RepeatDaily      bool    `json:"repeatDaily"`
	GeneralGoal      bool    `json:"generalGoal"`
	Streak           int32   `json:"streak"`
	NotifyEnabled    bool    `json:"notifyEnabled"`
	NotifyHour       int32   `json:"notifyHour"`
	NotifyMinute     int32   `json:"notifyMinute"`
	Category         string  `json:"category"`
	Subcategory      string  `json:"subcategory"`
	LastCompletedDay *string `json:"lastCompletedDay,omitempty"`
	UpdatedAtMillis  int64   `json:"updatedAtMillis"`
	ProjectPartsJSON string  `json:"projectPartsJson"`
	DailyCountsJSON  string  `json:"dailyCountsJson"`
}
type AgendaTemplateRecord struct {
	ID              string `json:"id"`
	Text            string `json:"text"`
	Category        string `json:"category"`
	Subcategory     string `json:"subcategory"`
	Favorite        bool   `json:"favorite"`
	BuiltIn         bool   `json:"builtIn"`
	Hidden          bool   `json:"hidden"`
	UpdatedAtMillis int64  `json:"updatedAtMillis"`
}
type AgendaCategoryRecord struct {
	ID              int64  `json:"id"`
	Category        string `json:"category"`
	Subcategory     string `json:"subcategory"`
	Hidden          bool   `json:"hidden"`
	UpdatedAtMillis int64  `json:"updatedAtMillis"`
}
type HiddenAgendaSuggestionRecord struct {
	Key            string `json:"key"`
	HiddenAtMillis int64  `json:"hiddenAtMillis"`
}
type PrivateActivity struct {
	ID              int64  `json:"id"`
	StartMillis     int64  `json:"startMillis"`
	EndMillis       *int64 `json:"endMillis,omitempty"`
	UpdatedAtMillis int64  `json:"updatedAtMillis"`
}

type DeletionRecord struct {
	Kind            string `json:"kind"`
	ID              int64  `json:"id"`
	UpdatedAtMillis int64  `json:"updatedAtMillis"`
}
