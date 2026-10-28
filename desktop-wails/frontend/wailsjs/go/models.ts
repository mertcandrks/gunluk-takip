export namespace main {
	
	export class AgendaCategoryRecord {
	    id: number;
	    category: string;
	    subcategory: string;
	    hidden: boolean;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new AgendaCategoryRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.category = source["category"];
	        this.subcategory = source["subcategory"];
	        this.hidden = source["hidden"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class AgendaRecord {
	    id: number;
	    day: string;
	    text: string;
	    task: boolean;
	    completed: boolean;
	    notDone: boolean;
	    repeatDaily: boolean;
	    generalGoal: boolean;
	    streak: number;
	    notifyEnabled: boolean;
	    notifyHour: number;
	    notifyMinute: number;
	    category: string;
	    subcategory: string;
	    lastCompletedDay?: string;
	    updatedAtMillis: number;
	    projectPartsJson: string;
	
	    static createFrom(source: any = {}) {
	        return new AgendaRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.day = source["day"];
	        this.text = source["text"];
	        this.task = source["task"];
	        this.completed = source["completed"];
	        this.notDone = source["notDone"];
	        this.repeatDaily = source["repeatDaily"];
	        this.generalGoal = source["generalGoal"];
	        this.streak = source["streak"];
	        this.notifyEnabled = source["notifyEnabled"];
	        this.notifyHour = source["notifyHour"];
	        this.notifyMinute = source["notifyMinute"];
	        this.category = source["category"];
	        this.subcategory = source["subcategory"];
	        this.lastCompletedDay = source["lastCompletedDay"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	        this.projectPartsJson = source["projectPartsJson"];
	    }
	}
	export class AgendaTemplateRecord {
	    id: string;
	    text: string;
	    category: string;
	    subcategory: string;
	    favorite: boolean;
	    builtIn: boolean;
	    hidden: boolean;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new AgendaTemplateRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.text = source["text"];
	        this.category = source["category"];
	        this.subcategory = source["subcategory"];
	        this.favorite = source["favorite"];
	        this.builtIn = source["builtIn"];
	        this.hidden = source["hidden"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class DeletionRecord {
	    kind: string;
	    id: number;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new DeletionRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.kind = source["kind"];
	        this.id = source["id"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class PrivateActivity {
	    id: number;
	    startMillis: number;
	    endMillis?: number;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new PrivateActivity(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.startMillis = source["startMillis"];
	        this.endMillis = source["endMillis"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class HiddenAgendaSuggestionRecord {
	    key: string;
	    hiddenAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new HiddenAgendaSuggestionRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.key = source["key"];
	        this.hiddenAtMillis = source["hiddenAtMillis"];
	    }
	}
	export class WaterRecord {
	    id: number;
	    timestampMillis: number;
	    amountMl: number;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new WaterRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.timestampMillis = source["timestampMillis"];
	        this.amountMl = source["amountMl"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class MealRecord {
	    id: number;
	    startMillis: number;
	    endMillis: number;
	    updatedAtMillis: number;
	    type: string;
	    itemsJson: string;
	    quick: boolean;
	
	    static createFrom(source: any = {}) {
	        return new MealRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.startMillis = source["startMillis"];
	        this.endMillis = source["endMillis"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	        this.type = source["type"];
	        this.itemsJson = source["itemsJson"];
	        this.quick = source["quick"];
	    }
	}
	export class SleepRecord {
	    id: number;
	    startMillis: number;
	    endMillis: number;
	    updatedAtMillis: number;
	
	    static createFrom(source: any = {}) {
	        return new SleepRecord(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.startMillis = source["startMillis"];
	        this.endMillis = source["endMillis"];
	        this.updatedAtMillis = source["updatedAtMillis"];
	    }
	}
	export class Snapshot {
	    generatedAtMillis: number;
	    activeSleepStartMillis?: number;
	    activeSleepUpdatedAtMillis: number;
	    sleeps: SleepRecord[];
	    activeMeal?: MealRecord;
	    meals: MealRecord[];
	    water: WaterRecord[];
	    waterGoalMl: number;
	    waterGoalUpdatedAtMillis: number;
	    agenda: AgendaRecord[];
	    agendaTemplates: AgendaTemplateRecord[];
	    agendaCategories: AgendaCategoryRecord[];
	    hiddenAgendaSuggestions: HiddenAgendaSuggestionRecord[];
	    privateActivities: PrivateActivity[];
	    deletions: DeletionRecord[];
	
	    static createFrom(source: any = {}) {
	        return new Snapshot(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.generatedAtMillis = source["generatedAtMillis"];
	        this.activeSleepStartMillis = source["activeSleepStartMillis"];
	        this.activeSleepUpdatedAtMillis = source["activeSleepUpdatedAtMillis"];
	        this.sleeps = this.convertValues(source["sleeps"], SleepRecord);
	        this.activeMeal = this.convertValues(source["activeMeal"], MealRecord);
	        this.meals = this.convertValues(source["meals"], MealRecord);
	        this.water = this.convertValues(source["water"], WaterRecord);
	        this.waterGoalMl = source["waterGoalMl"];
	        this.waterGoalUpdatedAtMillis = source["waterGoalUpdatedAtMillis"];
	        this.agenda = this.convertValues(source["agenda"], AgendaRecord);
	        this.agendaTemplates = this.convertValues(source["agendaTemplates"], AgendaTemplateRecord);
	        this.agendaCategories = this.convertValues(source["agendaCategories"], AgendaCategoryRecord);
	        this.hiddenAgendaSuggestions = this.convertValues(source["hiddenAgendaSuggestions"], HiddenAgendaSuggestionRecord);
	        this.privateActivities = this.convertValues(source["privateActivities"], PrivateActivity);
	        this.deletions = this.convertValues(source["deletions"], DeletionRecord);
	    }
	
		convertValues(a: any, classs: any, asMap: boolean = false): any {
		    if (!a) {
		        return a;
		    }
		    if (a.slice && a.map) {
		        return (a as any[]).map(elem => this.convertValues(elem, classs));
		    } else if ("object" === typeof a) {
		        if (asMap) {
		            for (const key of Object.keys(a)) {
		                a[key] = new classs(a[key]);
		            }
		            return a;
		        }
		        return new classs(a);
		    }
		    return a;
		}
	}
	export class AppState {
	    pairingCode: string;
	    addresses: string[];
	    status: string;
	    snapshot?: Snapshot;
	
	    static createFrom(source: any = {}) {
	        return new AppState(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.pairingCode = source["pairingCode"];
	        this.addresses = source["addresses"];
	        this.status = source["status"];
	        this.snapshot = this.convertValues(source["snapshot"], Snapshot);
	    }
	
		convertValues(a: any, classs: any, asMap: boolean = false): any {
		    if (!a) {
		        return a;
		    }
		    if (a.slice && a.map) {
		        return (a as any[]).map(elem => this.convertValues(elem, classs));
		    } else if ("object" === typeof a) {
		        if (asMap) {
		            for (const key of Object.keys(a)) {
		                a[key] = new classs(a[key]);
		            }
		            return a;
		        }
		        return new classs(a);
		    }
		    return a;
		}
	}
	
	
	
	
	
	

}

