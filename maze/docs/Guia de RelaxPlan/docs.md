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

### `maxRounds(int)`
Defineix el **màxim de rondes** de relaxació. Evita bucles llargs si el mapa és molt restrictiu.

```java
.maxRounds(20)
```

---

## 3. Com es construeix un `RelaxPlan` complet

Flux correcte:

1. `RelaxPlan.builder()`
2. Configures opcions (`order`, `step`, `floor`, `cooldown`, `mode`, `maxRounds`)
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
            .maxRounds(30)
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
- `maxRounds`: 64

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

---

## 6. Patró recomanat per mantenir-ho net

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
