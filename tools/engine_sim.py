# Line-by-line port of ProgressionEngine.kt. A second, independent implementation of the same
# rules: if the Kotlin and this disagree, one of them has a bug. Run it with `python3
# tools/engine_sim.py` alongside `./gradlew :app:testDebugUnitTest`.
#
# Sets carry their OWN rung, not just the session's, because a session can mix rungs (the seeded
# pull-up does: sets 0-1 unassisted at "standard", sets 2-4 at "band_assisted").
from dataclasses import dataclass, field, replace
from typing import List, Optional

@dataclass
class L:
    """One logged set: index, reps, and the rung it was ACTUALLY performed at."""
    setIndex: int; reps: int; levelKey: Optional[str]

@dataclass
class S:
    epochDay: int; levelKey: Optional[str]; weightKg: Optional[float]; sets: List[L]
    @property
    def reps(self): return [x.reps for x in self.sets]
    def sets_at(self, level):
        # A null level means "no ladder" (weighted / core), so every set counts.
        return self.sets if level is None else [x for x in self.sets if x.levelKey == level]
    def reps_at(self, level): return [x.reps for x in self.sets_at(level)]
    @property
    def top(self): return max(self.reps) if self.reps else 0
    @property
    def low(self): return min(self.reps) if self.reps else 0
    @property
    def empty(self): return len(self.sets) == 0

def uniform(epochDay, levelKey, weightKg, reps):
    """Every set at the session's own rung: the ordinary, non-mixed case."""
    return S(epochDay, levelKey, weightKg, [L(i, r, levelKey) for i, r in enumerate(reps)])

@dataclass
class Ex:
    exerciseId: str; type: str; hypertrophyMin: int; hypertrophyMax: int
    rollingWindow: int; progressionTracked: bool
    levelKeysAscending: List[str] = field(default_factory=list)
    currentLevelKey: Optional[str] = None
    currentWeightKg: Optional[float] = None
    weightIncrementKg: Optional[float] = None

def qualifies(s, hmax, level=None):
    # Only the sets at the rung being evaluated; sets at other rungs must not drag the min down.
    reps = s.reps_at(level)
    return bool(reps) and min(reps) >= hmax

def streak(recent, hmax, level=None):
    n = 0
    for s in recent:
        if qualifies(s, hmax, level): n += 1
        else: break
    return n

def evaluate(e, history):
    if not e.progressionTracked or e.type == "CORE": return ("Hold", 0, 0)
    w = max(e.rollingWindow, 1)
    if e.type == "BODYWEIGHT_PROGRESSION":
        cur = e.currentLevelKey
        if cur is None: return ("Hold", 0, w)
        # A session counts if it contains work AT this rung, and only those sets are judged.
        at = [s for s in history if s.sets_at(cur)]
        st = streak(at[:w], e.hypertrophyMax, cur)
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

def rung_history(history, level, weight=None):
    """The history belonging to one rung: the level if there is a ladder, else the load."""
    if level is not None: return [s for s in history if s.sets_at(level)]
    if weight is not None:
        return [s for s in history if s.weightKg is not None and abs(s.weightKg - weight) < 0.001]
    return history

def baseline(history, level, weight=None):
    for s in rung_history(history, level, weight):
        reps = s.reps_at(level)
        if reps: return max(reps)
    return None

def pr(history, level, weight=None):
    allreps = [r for s in rung_history(history, level, weight) for r in s.reps_at(level)]
    return max(allreps) if allreps else None

def default_reps(setIndex, isFixed, goal, history, level, hmin, weight=None):
    if isFixed: return goal
    last = next((s for s in rung_history(history, level, weight) if s.reps_at(level)), None)
    if last is None: return hmin
    # Match the stored set index, so filtering out other rungs cannot shift set 3 onto set 1.
    for x in last.sets:
        if x.setIndex == setIndex and x.levelKey == level: return x.reps
    reps = last.reps_at(level)
    return max(reps) if reps else hmin

# ---------------- fixtures mirroring the Kotlin tests ----------------
PULLUP = Ex("pull_up","BODYWEIGHT_PROGRESSION",8,12,6,True,
    ["dead_hang","scapular_pull","negative","band_assisted","standard","weighted","archer"],
    currentLevelKey="band_assisted")
PRESS = Ex("standing_db_press","WEIGHTED",8,12,6,True,currentWeightKg=10.0,weightIncrementKg=2.0)

def sessions(count, reps, level=None, weight=None, start=1000):
    return [uniform(start-i, level, weight, list(reps)) for i in range(count)]

def pullup_session(day, unassisted, banded):
    """The real seeded pull-up shape: unassisted at "standard", the rest band assisted."""
    sets = [L(i, r, "standard") for i, r in enumerate(unassisted)]
    sets += [L(len(unassisted)+i, r, "band_assisted") for i, r in enumerate(banded)]
    return S(day, "band_assisted", None, sets)

checks = []
def check(name, cond): checks.append((name, bool(cond)))

o = evaluate(PULLUP, sessions(5,[12]*5,level="band_assisted"))
check("no suggestion before window full", o[0]=="Hold" and o[1]==5)

o = evaluate(PULLUP, sessions(6,[12]*5,level="band_assisted"))
check("six qualifying -> advance to standard", o==("AdvanceLevel","band_assisted","standard"))

hist = [uniform(1000,"band_assisted",None,[12,12,11])] + sessions(6,[12,12,12],level="band_assisted",start=999)
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

hist = [uniform(1000,"band_assisted",None,[9,8,8]),
        uniform(999,"band_assisted",None,[11,10,9]),
        uniform(998,"negative",None,[15,14,14])]
check("PR per (exercise,level) band_assisted=11", pr(hist,"band_assisted")==11)
check("PR per (exercise,level) negative=15", pr(hist,"negative")==15)
check("baseline = latest at level = 9", baseline(hist,"band_assisted")==9)

h = sessions(6,[12,12],level="band_assisted")
check("fresh level has no baseline", baseline(h,"standard") is None)
check("fresh level has no PR", pr(h,"standard") is None)

check("FIXED_REP prefills goal", default_reps(2,True,8,[],"band_assisted",8)==8)
hist2=[uniform(1000,"negative",None,[7,6,5]), uniform(999,"negative",None,[4,4,4])]
check("TO_FAILURE prefills same set index from last session", default_reps(1,False,0,hist2,"negative",8)==6)
check("TO_FAILURE cold start = hypertrophyMin", default_reps(0,False,0,[],"full_nordic_curl",6)==6)

# regression: drop to an easier rung, comparison target follows that rung's own history
down = replace(PULLUP, currentLevelKey="negative")
check("regressing resets target to that level's history", evaluate(down, hist)[0]=="Hold")
check("regressed baseline reads negative history", baseline(hist,"negative")==15)

# --- day completion ---
# Atwater factors, mirroring domain/Calories.kt.
def kcal(protein, carbs, fat):
    return max(protein,0)*4 + max(carbs,0)*4 + max(fat,0)*9

def day(water,protein,carbs,fat,cal,workout_day,workout_done,auto=True,
        g=(3000,140,250,115,2600)):
    """
    The fourth macro slot holds FAT when calories are auto-derived and CALORIES when they are
    entered by hand - exactly one of the two, never both. That is what keeps the denominator at
    5 on training days and 4 otherwise even though a macro was added.
    """
    b=[water>=g[0], protein>=g[1], carbs>=g[2]]
    b.append(fat>=g[3] if auto else cal>=g[4])
    if workout_day: b.append(workout_done)
    return sum(b), len(b), sum(b)/len(b)

check("workout day denominator 5", day(0,0,0,0,0,True,False)[1]==5)
check("rest day denominator 4", day(0,0,0,0,0,False,False)[1]==4)
check("denominator still 5 with manual calories",
      day(0,0,0,0,0,True,False,auto=False)[1]==5)
check("3/5 = 0.6 fill", abs(day(3000,140,250,0,0,True,False)[2]-0.6)<1e-6)
check("crown on 5/5", day(3200,150,260,120,2700,True,True)[2]==1.0)
check("rest day crowns on 4", day(3000,140,250,115,2600,False,False)[2]==1.0)
check("no crown when fat is missed",
      day(3000,140,250,0,2600,False,False)[2]<1.0)

# Atwater parity with the Kotlin
check("atwater 4/4/9", (kcal(1,0,0),kcal(0,1,0),kcal(0,0,1))==(4,4,9))
check("protein+carbs alone undershoot the 2600 goal", kcal(140,250,0)==1560)
check("115g fat closes the gap", kcal(140,250,115)==2595)

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

# ---------------- mixed-rung sessions (the pull-up bug) ----------------
# Six sessions where every band-assisted set tops the range but the unassisted ones never do.
mixed = [pullup_session(1000-i, [4,3], [12,12,12]) for i in range(6)]
o = evaluate(PULLUP, mixed)
check("unassisted sets do not block band assisted progression",
      o[0]=="AdvanceLevel" and o[2]=="standard")

weak = [pullup_session(1000-i, [4,3], [12,11,12]) for i in range(6)]
check("a weak band assisted set still blocks", evaluate(PULLUP, weak)[0]=="Hold")

one = [pullup_session(1000, [6,5], [12,11,10])]
check("PR does not leak across rungs in one session", pr(one,"band_assisted")==12)
check("unassisted PR reads only standard sets", pr(one,"standard")==6)

one2 = [pullup_session(1000, [6,5], [9,9,8])]
check("baseline reads only the rung asked for", baseline(one2,"band_assisted")==9)
check("baseline at standard = 6", baseline(one2,"standard")==6)
check("TO_FAILURE prefill matches stored set index at rung",
      default_reps(1,False,0,one2,"standard",8)==5)

# ---------------- weighted: the load IS the rung ----------------
wh = sessions(1,[12,12,12],weight=10.0,start=1000) + sessions(1,[8,8,7],weight=12.0,start=999)
check("weighted PR does not leak across loads", pr(wh,None,weight=12.0)==8)
check("weighted PR at 10kg = 12", pr(wh,None,weight=10.0)==12)

back = sessions(1,[6,6,5],weight=12.0,start=1000) + sessions(1,[11,10,10],weight=10.0,start=999)
check("dropping back to lighter load reads that load's history",
      baseline(back,None,weight=10.0)==11)

only10 = sessions(3,[12,12,12],weight=10.0)
check("a load with no history has no PR", pr(only10,None,weight=14.0) is None)
check("a load with no history has no baseline", baseline(only10,None,weight=14.0) is None)

fails=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
print()
print(f"{len(checks)-len(fails)}/{len(checks)} passed")
