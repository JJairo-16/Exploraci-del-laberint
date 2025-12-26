# Guia d’ús del builder de `RelaxPlan`

Aquesta guia explica com configurar un `RelaxPlan` amb el patró *builder* per controlar com es relaxen les restriccions durant la col·locació d’items.

> Nota: `RelaxPlan` és **immutable**. Has de configurar-ho tot al *builder* i després fer `build()`.

---

## 1. Com s’aplica a un `ItemType`

Per utilitzar un `RelaxPlan`, normalment sobrescrius `getRelaxPlan()` dins del teu `ItemType` (classe o `enum`) i hi retornes el pla construït.

```java
@Override
public RelaxPlan getRelaxPlan() {
    return RelaxPlan.builder()
            .build();
}
```

---

## 2. Paràmetres del builder (què fa cadascun)

### `order(List<Constraint>)`
Defineix **l’ordre** en què es provarà de relaxar cada restricció.

- Si una restricció **no és a `order`**, **mai** es relaxarà.

```java
.order(List.of(Constraint.BETWEEN, Constraint.PLAYER, Constraint.EXIT, Constraint.BORDER))
```

---

### `step(Constraint, int)`
Defineix **quant baixa** una restricció quan es relaxa (mínim 1).

```java
.step(Constraint.EXIT, 3)      // baixa de 3 en 3
.step(Constraint.PLAYER, 1)    // baixa de 1 en 1
```

---

### `floor(Constraint, int)`
Defineix el **límit inferior**: fins a quin valor mínim pot baixar (mínim 0).

- Quan el valor arriba a `floor`, aquella restricció **ja no baixa més**.

```java
.floor(Constraint.PLAYER, 2)   // mai baixarà per sota de 2
```

---

### `cooldown(Constraint, int)`
Defineix cada quantes **rondes** es permet relaxar aquella restricció (mínim 1).

- `1` = es pot relaxar cada ronda  
- `2` = es relaxa un cop cada 2 rondes  
- `5` = es relaxa molt més lentament

```java
.cooldown(Constraint.EXIT, 4)  // només es pot relaxar EXIT cada 4 rondes
```

---

### `mode(RelaxPlan.Mode)`
Defineix **com** es relaxa en cada ronda:

- `ONE_PER_ROUND`: relaxa **una sola** restricció per ronda (cíclic segons `order`)
- `ALL_EACH_ROUND`: relaxa **totes les possibles** a cada ronda (seguint `order`)

```java
.mode(RelaxPlan.Mode.ONE_PER_ROUND)
```

---

### `scanMode(RelaxPlan.ScanMode)`
Defineix el tipus d'escàner que es realitza en les comprovacions locals (àrea 3x3) al voltant d'una posició candidata:

- `NONE`: no es realitza cap escaneig; només s'apliquen les restriccions de distància.
- `SAME_TYPE_EXACT`: impedeix la col·locació si hi ha un item adjacent del mateix tipus exacte (mateix `id`).
- `SAME_TYPE_GENERAL`: impedeix la col·locació si hi ha un item adjacent del mateix tipus general (`BasicItemType`, `PowerType` o `SpecialType`).
- `ANY_TYPE`: impedeix la col·locació si hi ha qualsevol item adjacent, independentment del seu tipus.

```java
.scanMode(ScanMode.SAME_TYPE_EXACT)
```

---

### `maxRounds(int)`
Defineix el **màxim de rondes** de relaxació. Evita bucles llargs si el mapa és molt restrictiu.

```java
.maxRounds(20)
```

---

### `maxStallRounds(int)`
Defineix el màxim de rondes consecutives sense canvis abans d’abandonar la relaxació.

- Serveix per evitar tallar massa aviat quan totes les restriccions estan temporalment en `cooldown`.
- No pot provocar bucles infinits perquè `maxRounds` continua sent el límit superior.

```java
.maxStallRounds(2)
```

Interpretació:

- `0` → comportament estricte (es talla a la primera ronda sense canvis)
- `1-2` → recomanat
- `>= cooldown màxim - 1` o `-1` (càlcul automàtic) → evita pràcticament tots els “talls falsos”

---

### `weightDecay(double)`
Defineix el pes de les rondes al decidir (a major quantitat de rondes, menys estrictes són).


```java
.weightDecay(0.5)
```

---

### `weightFunction(IntToDoubleFunction)`
Defineix la formula amb la qual es calcularà el pes.

```java
.weightFunction(r -> {
  return Math.pow(0.5, Math.max(0, r)) * (r * 0.9);
})
```

---

## 3. Com es construeix un `RelaxPlan` complet

Flux correcte:

1. `RelaxPlan.builder()`
2. Configures opcions (`order`, `step`, `floor`, `cooldown`, `mode`, `scanMode`, `maxStallRounds`, `maxRounds`, `weightDecay`/`weightFunction`)
3. `build()`

```java
@Override
public RelaxPlan getRelaxPlan() {
    return RelaxPlan.builder()
            .order(List.of(Constraint.BETWEEN, Constraint.PLAYER, Constraint.EXIT, Constraint.BORDER))
            .step(Constraint.BETWEEN, 1)
            .step(Constraint.EXIT, 2)
            .floor(Constraint.PLAYER, 2)
            .floor(Constraint.EXIT, 3)
            .cooldown(Constraint.PLAYER, 1)
            .cooldown(Constraint.EXIT, 4)
            .mode(RelaxPlan.Mode.ONE_PER_ROUND)
            .scanMode(ScanMode.SAME_TYPE_EXACT)
            .maxStallRounds(2)
            .maxRounds(30)
            .weightDecay(0.5)
            .build();
}
```

---

## 4. Valors per defecte (si no configures res)

Si només fas:

```java
RelaxPlan.builder().build();
```

Aleshores el comportament per defecte és:

- `order`: `PLAYER, BETWEEN, EXIT, BORDER`
- `step`: 1
- `floor`: 0
- `cooldown`: 1
- `mode`: `ONE_PER_ROUND`
- `scanMode`: `NONE`
- `maxStallRounds`: 2
- `maxRounds`: 64
- `weightDecay`: 0.9
- `weightFunction`: r -> $\text{weightDecay}^{\max(0, r)}$

---

## 5. Regles pràctiques (per no embolicar-te)

- **Vols que una restricció no es relaxi mai?**  
  No la posis a `order(...)`.

- **Vols que es relaxi més lentament?**  
  Posa-li un `cooldown(...)` més alt.

- **Vols que no baixi mai de cert valor?**  
  Posa-li un `floor(...)` > 0.

- **Vols relaxació agressiva?**  
  `mode(ALL_EACH_ROUND)` + `step` més grans (amb cura).

- **No vols tallar per una ronda buida?**
  Usa `maxStallRounds(1)` o `maxStallRounds(2)`.

- **Vols relaxació agressiva però controlada?**
  `ALL_EACH_ROUND` + `maxStallRounds petit` + `maxRounds limitat`.

---

## 6. Patró recomanat per mantenir-ho net

### Patró 1

Si tens molts ítems amb plans semblants, pots definir un `RelaxPlan` reutilitzable (constant) i retornar-lo:

```java
private static final RelaxPlan PLAN_COMU = RelaxPlan.builder()
        .maxRounds(20)
        .build();

@Override
public RelaxPlan getRelaxPlan() {
    return PLAN_COMU;
}
```

Això evita repetir configuració i manté el codi més clar.

### Patró 2

Si tens molts ítems amb patrons personalitzats cada un o varis agrupacions, pots definir un mètode privat amb el pla predeterminat i la serie de configuracions com a mètodes per gestionar-los en un switch (com `Override`):

```java
@Override
public RelaxPlan getRelaxPlan() {
    return switch(this) {
        case PORTAL_GUN -> portalGunRelaxPlan();
        default -> defaultRelaxPlan();
    };

}

private RelaxPlan defaultRelaxPlan() {
    return RelaxPlan.builder().build();
}

private RelaxPlan portalGunRelaxPlan() {
    return RelaxPlan.builder()
            .floor(Constraint.BETWEEN, 20)
            .build();
}
```

Això permet canviar la configuració amb major fluides sense sacrificar la legibilitat.