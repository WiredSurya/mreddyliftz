# Line-by-line port of ProgressionEngine.kt used to verify the rules and the unit-test
# expectations, since this sandbox has no Android SDK / Maven access to compile Kotlin.
from dataclasses import dataclass, field, replace
from typing import List, Optional

@dataclass
class S:
    epochDay: int; levelKey: Optional[str]; weightKg: Optional[float]; reps: List[int]
    @property
    def top(self): return max(self.reps) if self.reps else 0
    @property
    def low(self): return min(self.reps) if self.reps else 0
    @property
    def empty(self): return len(self.reps) == 0

@dataclass
class Ex:
    exerciseId: str; type: str; hypertrophyMin: int; hypertrophyMax: int
    rollingWindow: int; progressionTracked: bool
    levelKeysAscending: List[str] = field(default_factory=list)
    currentLevelKey: Optional[str] = None
    currentWeightKg: Optional[float] = None
    weightIncrementKg: Optional[float] = None

def qualifies(s, hmax): return (not s.empty) and s.low >= hmax
def streak(recent, hmax):
    n = 0
    for s in recent:
        if qualifies(s, hmax): n += 1
        else: break
    return n

def evaluate(e, history):
    if not e.progressionTracked or e.type == "CORE": return ("Hold", 0, 0)
    w = max(e.rollingWindow, 1)
    if e.type == "BODYWEIGHT_PROGRESSION":
        cur = e.currentLevelKey
        if cur is None: return ("Hold", 0, w)
        at = [s for s in history if s.levelKey == cur]
        st = streak(at[:w], e.hypertrophyMax)
        if st < w: return ("Hold", st, w)
        i = e.levelKeysAscending.index(cur) if cur in e.levelKeysAscending else -1
        if i < 0: return ("Hold", st, w)
        if i >= len(e.levelKeysAscending) - 1: return ("TopOfLadder", cur, None)
        return ("AdvanceLevel", cur, e.levelKeysAscending[i+1])
    if e.type == "WEIGHTED":
        cur = e.currentWeightKg
        if cur is None: return ("Hold", 0, w)
        inc = e.weightIncrementKg or 0.0
        if inc <= 0: return ("Hold", 0, w)
        at = [s for s in history if s.weightKg is not None and abs(s.weightKg - cur) < 0.001]
        st = streak(at[:w], e.hypertrophyMax)
        if st < w: return ("Hold", st, w)
        return ("AddWeight", cur, cur + inc)
    return ("Hold", 0, 0)

def baseline(history, level):
    rel = history if level is None else [s for s in history if s.levelKey == level]
    for s in rel:
        if not s.empty: return s.top
    return None

def pr(history, level):
    rel = history if level is None else [s for s in history if s.levelKey == level]
    allreps = [r for s in rel for r in s.reps]
    return max(allreps) if allreps else None

def default_reps(setIndex, isFixed, goal, history, level, hmin):
    if isFixed: return goal
    rel = history if level is None else [s for s in history if s.levelKey == level]
    last = next((s for s in rel if not s.empty), None)
    if last is None: return hmin
    return last.reps[setIndex] if setIndex < len(last.reps) else last.top

# ---------------- fixtures mirroring the Kotlin tests ----------------
PULLUP = Ex("pull_up","BODYWEIGHT_PROGRESSION",8,12,6,True,
    ["dead_hang","scapular_pull","negative","band_assisted","standard","weighted","archer"],
    currentLevelKey="band_assisted")
PRESS = Ex("standing_db_press","WEIGHTED",8,12,6,True,currentWeightKg=10.0,weightIncrementKg=2.0)

def sessions(count, reps, level=None, weight=None, start=1000):
    return [S(start-i, level, weight, list(reps)) for i in range(count)]

checks = []
def check(name, cond): checks.append((name, bool(cond)))

o = evaluate(PULLUP, sessions(5,[12]*5,level="band_assisted"))
check("no suggestion before window full", o[0]=="Hold" and o[1]==5)

o = evaluate(PULLUP, sessions(6,[12]*5,level="band_assisted"))
check("six qualifying -> advance to standard", o==("AdvanceLevel","band_assisted","standard"))

hist = [S(1000,"band_assisted",None,[12,12,11])] + sessions(6,[12,12,12],level="band_assisted",start=999)
o = evaluate(PULLUP, hist)
check("one weak set breaks streak", o[0]=="Hold" and o[1]==0)

o = evaluate(PULLUP, sessions(6,[12,12,12],level="negative"))
check("other level does not count", o[0]=="Hold")

o = evaluate(replace(PULLUP,currentLevelKey="archer"), sessions(6,[12,12,12],level="archer"))
check("top of ladder", o[0]=="TopOfLadder")

o = evaluate(PRESS, sessions(6,[12,12,12],weight=10.0))
check("weighted advance 10 -> 12", o==("AddWeight",10.0,12.0))

o = evaluate(PRESS, sessions(6,[12,12,12],weight=8.0))
check("lighter weight history does not earn jump", o[0]=="Hold")

core = replace(PULLUP, type="CORE", progressionTracked=False)
check("core never progresses", evaluate(core, sessions(20,[50],level="band_assisted"))[0]=="Hold")

short = replace(PULLUP, rollingWindow=3)
h3 = sessions(3,[12,12,12],level="band_assisted")
check("window 3 advances", evaluate(short,h3)[0]=="AdvanceLevel")
check("window 6 holds on 3", evaluate(PULLUP,h3)[0]=="Hold")

hist = [S(1000,"band_assisted",None,[9,8,8]),
        S(999,"band_assisted",None,[11,10,9]),
        S(998,"negative",None,[15,14,14])]
check("PR per (exercise,level) band_assisted=11", pr(hist,"band_assisted")==11)
check("PR per (exercise,level) negative=15", pr(hist,"negative")==15)
check("baseline = latest at level = 9", baseline(hist,"band_assisted")==9)

h = sessions(6,[12,12],level="band_assisted")
check("fresh level has no baseline", baseline(h,"standard") is None)
check("fresh level has no PR", pr(h,"standard") is None)

check("FIXED_REP prefills goal", default_reps(2,True,8,[],"band_assisted",8)==8)
hist2=[S(1000,"negative",None,[7,6,5]), S(999,"negative",None,[4,4,4])]
check("TO_FAILURE prefills same set index from last session", default_reps(1,False,0,hist2,"negative",8)==6)
check("TO_FAILURE cold start = hypertrophyMin", default_reps(0,False,0,[],"full_nordic_curl",6)==6)

# regression: drop to an easier rung, comparison target follows that rung's own history
down = replace(PULLUP, currentLevelKey="negative")
check("regressing resets target to that level's history", evaluate(down, hist)[0]=="Hold")
check("regressed baseline reads negative history", baseline(hist,"negative")==15)

# --- day completion ---
def day(water,protein,carbs,cal,workout_day,workout_done,g=(3000,140,250,2600)):
    b=[water>=g[0],protein>=g[1],carbs>=g[2],cal>=g[3]]
    if workout_day: b.append(workout_done)
    return sum(b), len(b), sum(b)/len(b)
check("workout day denominator 5", day(0,0,0,0,True,False)[1]==5)
check("rest day denominator 4", day(0,0,0,0,False,False)[1]==4)
check("3/5 = 0.6 fill", abs(day(3000,140,250,0,True,False)[2]-0.6)<1e-6)
check("crown on 5/5", day(3200,150,260,2700,True,True)[2]==1.0)
check("rest day crowns on 4", day(3000,140,250,2600,False,False)[2]==1.0)

# --- time estimator ---
def est(durs,w,sets,rest):
    win=[d for d in durs[:max(w,1)] if d>0]
    return sets*(rest+40) if not win else sum(win)//len(win)
check("cold start estimate", est([],6,3,90)==3*(90+40))
check("rolling window ignores old outlier", est([300,300,3000],2,3,90)==300)
def remaining(per,idx,done,planned):
    t=0
    for i,s in enumerate(per):
        if idx is None or i>idx: t+=s
        elif i==idx:
            p=max(planned,1); t+=int(s*(max(planned-done,0)/p))
    return t
check("remaining pro-rates in-progress", remaining([600,400,200],1,2,4)==400)

fails=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
print()
print(f"{len(checks)-len(fails)}/{len(checks)} passed")
