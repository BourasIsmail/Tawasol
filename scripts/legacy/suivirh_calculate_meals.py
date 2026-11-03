"""Port fidèle de DeplacementController::calculateMeals (Suivi RH) pour vérifier le moteur de calcul sur les données réelles."""
from datetime import datetime
def meals(dep, ret):
    total = int((ret - dep).total_seconds() // 60)
    h, m = dep.hour, dep.minute
    if (h == 18 and m > 0) or (18 < h <= 23) or (h == 0 and m == 0):
        first = 'ftour'
        target = dep.replace(hour=0, minute=0, second=0)
        if h > 0:
            from datetime import timedelta
            target = target + timedelta(days=1)
        total -= int(abs((target - dep).total_seconds()) // 60)
    elif (h == 0 and m > 0) or (0 < h < 11) or (h == 11 and m == 0):
        first = 'ghda'; target = dep.replace(hour=11, minute=0, second=0); total -= int(abs((target - dep).total_seconds()) // 60)
    elif (h == 11 and m > 0) or (11 < h < 18) or (h == 18 and m == 0):
        first = 'isha'; target = dep.replace(hour=18, minute=0, second=0); total -= int(abs((target - dep).total_seconds()) // 60)
    fd = 0
    if first == 'ftour':
        fd += total - 300 >= 0; fd += total - 300 - 360 - 180 >= 0; fd += total - 300 - 360 - 180 - 240 - 180 >= 0
    elif first == 'ghda':
        fd += total - 180 >= 0; fd += total - 180 - 240 - 180 >= 0
    else:
        fd += total - 180 >= 0
    fd = int(fd)
    cur, rep = first, 0
    nxt = {'ftour': ('ghda', 300, 360), 'ghda': ('isha', 180, 240), 'isha': ('ftour', 180, 180)}
    while total > 0:
        n, a, b = nxt[cur]
        if total - a >= 0:
            rep += 1; total -= a; total -= b; cur = n
        else:
            total = 0
    t1 = t2 = 0
    if rep > 0:
        day, rem = 1, rep
        while rem > 0:
            if day == 1:
                t1 += fd if fd <= rem else rem
                rem -= fd; day += 1
            elif day < 16:
                k = min(rem, 3); t1 += k; rem -= k; day += 1
            else:
                k = min(rem, 3); t2 += k; rem -= k; day += 1
    return t1, t2
