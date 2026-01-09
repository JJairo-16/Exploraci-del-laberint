# Guia d’ús del builder de `RelaxPlan`

Aquesta guia explica com configurar un `RelaxPlan` amb el patró *builder* per controlar com es relaxen les restriccions durant la col·locació d’items.

> Nota: `RelaxPlan` és **immutable**. Has de configurar-ho tot al *builder* i després fer `build()`.
>
> ---

## Índex
- [Guia d’ús del builder de `RelaxPlan`](#guia-dús-del-builder-de-relaxplan)
  - [Índex](#índex)
  - [1. Com s’aplica a un `ItemType`](#1-com-saplica-a-un-itemtype)
  - [2. Paràmetres del builder (què fa cadascun)](#2-paràmetres-del-builder-què-fa-cadascun)
    - [`order(List<Constraint>)`](#orderlistconstraint)
    - [`step(Constraint, int)`](#stepconstraint-int)
    - [`floor(Constraint, int)`](#floorconstraint-int)
    - [`cooldown(Constraint, int)`](#cooldownconstraint-int)
    - [`mode(RelaxPlan.Mode)`](#moderelaxplanmode)
    - [`scanMode(RelaxPlan.ScanMode)`](#scanmoderelaxplanscanmode)
    - [`customScanAgainst(ItemType...)`](#customscanagainstitemtype)
    - [`distComparisonMode(RelaxPlan.DistComparisonMode)`](#distcomparisonmoderelaxplandistcomparisonmode)
    - [`customDistAgainst(ItemType...)`](#customdistagainstitemtype)
    - [`maxRounds(int)`](#maxroundsint)
    - [`maxStallRounds(int)`](#maxstallroundsint)
    - [`weightDecay(double)`](#weightdecaydouble)
    - [`weightFunction(IntToDoubleFunction)`](#weightfunctioninttodoublefunction)
    - [`precheckPlayerDistance(boolean)`](#precheckplayerdistanceboolean)
    - [`forcePlaceIfPrecheckFails(boolean)`](#forceplaceifprecheckfailsboolean)
  - [3. Com es construeix un `RelaxPlan` complet](#3-com-es-construeix-un-relaxplan-complet)
  - [4. Valors per defecte (si no configures res)](#4-valors-per-defecte-si-no-configures-res)
  - [5. Regles pràctiques (per no embolicar-te)](#5-regles-pràctiques-per-no-embolicar-te)
  - [6. Patró recomanat per mantenir-ho net](#6-patró-recomanat-per-mantenir-ho-net)
    - [Patró 1](#patró-1)
    - [Patró 2](#patró-2)

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

### `customScanAgainst(ItemType...)`
Defineix el conjunt d’<code>ItemType</code> contra els quals s’aplica l’escaneig local quan el
<code>scanMode</code> és <code>CUSTOM</code>. Només es considera conflicte si un item adjacent
pertany a aquest conjunt explícitament definit.

- Només és vàlid quan `scanMode` és `CUSTOM`.
- La llista d’<code>ItemType</code> ha de ser no buida.
- Si no es proporciona cap tipus, la construcció del <code>RelaxPlan</code> falla.

```java
.customScanAgainst(BasicItemType.MAP, BasicItemType.PORTAL_GUN)
```

---

### `distComparisonMode(RelaxPlan.DistComparisonMode)`
Defineix com s’aplica la restricció de distància mínima entre items (`minDistBetween`). Determina **contra quins items** es compara aquesta distància quan es valida una posició candidata:

- `SAME_TYPE_EXACT`: la distància mínima només s’aplica contra items del mateix tipus exacte (mateix `id`).
- `SAME_TYPE_GENERAL`: la distància mínima només s’aplica contra items del mateix tipus general (`BasicItemType`, `PowerType` o `SpecialType`).
- `ANY_TYPE`: la distància mínima s’aplica contra qualsevol item, independentment del seu tipus.

```java
.distComparisonMode(DistComparisonMode.SAME_TYPE_EXACT)
```

---

### `customDistAgainst(ItemType...)`
Defineix el conjunt d’<code>ItemType</code> contra els quals s’aplica la restricció de distància
mínima entre items (`minDistBetween`) quan el <code>distComparisonMode</code> és
<code>CUSTOM</code>.

- Només és vàlid quan `distComparisonMode` és `CUSTOM`.
- La llista d’<code>ItemType</code> ha de ser no buida.
- La distància mínima només es comprova contra items del conjunt especificat.

```java
.customDistAgainst(BasicItemType.MAP)
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

### `precheckPlayerDistance(boolean)`
Defineix si s’ha de fer una comprovació prèvia estricta de la distància al jugador abans de col·locar l’objecte.

```java
.precheckPlayerDistance(true)
```

---

### `forcePlaceIfPrecheckFails(boolean)`
Defineix si s’ha de forçar la col·locació de l’objecte quan la comprovació prèvia de distància al jugador no produeix cap candidat vàlid.

```java
.forcePlaceIfPrecheckFails(true)
```

---

## 3. Com es construeix un `RelaxPlan` complet

Flux correcte:

1. `RelaxPlan.builder()`
2. Configures opcions (`order`, `step`, `floor`, `cooldown`, `mode`, `scanMode`, `distComparisonMode`, `maxStallRounds`, `maxRounds`, `weightDecay`/`weightFunction`, `precheckPlayerDistance`, `forcePlaceIfPrecheckFails`)
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
            .scanMode(ScanMode.CUSTOM)
            .customScanAgainst(Items.ITEM1, Items.ITEM2)
            .distComparisonMode(DistComparisonMode.CUSTOM)
            .customDistAgainst(Items.ITEM1, Items.ITEM2)
            .maxStallRounds(2)
            .maxRounds(30)
            .weightDecay(0.5)
            .precheckPlayerDistance(true)
            .forcePlaceIfPrecheckFails(true)
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
- `SAME_TYPE_GENERAL`: `ANY_TYPE`
- `maxStallRounds`: 2
- `maxRounds`: 64
- `weightDecay`: 0.9
- `weightFunction`: r -> $\text{weightDecay}^{\max(0, r)}$
  > (`r` = nº de la ronda)

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

Si tens molts ítems amb patrons personalitzats (individuals o en grup), es recomana **definir els `RelaxPlan` com a constants immutables** i retornar-les des del `switch` dins del `override`.  
Això evita crear objectes repetidament i manté una estructura clara i eficient.

```java
@Override
public RelaxPlan getRelaxPlan() {
    return switch (this) {
        case PORTAL_GUN -> PORTAL_GUN_RELAX_PLAN;
        default -> DEFAULT_RELAX_PLAN;
    };
}

private static final RelaxPlan DEFAULT_RELAX_PLAN = RelaxPlan.builder().build();

private static final RelaxPlan PORTAL_GUN_RELAX_PLAN = RelaxPlan.builder()
            .floor(Constraint.BETWEEN, 20)
            .build();
```

Aquest patró:
- evita la creació innecessària d’objectes,
- deixa clar que el `RelaxPlan` és configuració immutable i no estat,
- millora el rendiment en bucles de col·locació,
- facilita la lectura, el manteniment i l’extensió del codi.
