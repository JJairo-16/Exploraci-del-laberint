# The Ghost Room

---

## Què és?

*The Ghost Room* (o *Ghost Room*) és un **heisenbug conegut** que **no serà solucionat completament**, sinó **mitigat / desactivat**.

Primera imatge capturada:  
![Imatge de la Ghost Room](img/the%20ghost%20room.png)

---

## Causa

Aquest comportament és causat pel sistema de **generació del mapa**.  
Tot i que és **impossible de reproduir intencionalment**, depèn completament
del **factor aleatori (RNG)**.

---

## Símptomes

### Spawn

El jugador, en lloc d’aparèixer dins del laberint, apareix en una **habitació buida** amb una única sortida en forma de passadís.

Aquesta sortida és **intransitable** a causa d’un **terra amb col·lisió fantasma**, que incompleix les regles de col·lisió del joc tot i que, aparentment, les compleix.

En altres paraules, *The Ghost Room* representa un estat en què el sistema de generació produeix un mapa **lògicament vàlid**, però el sistema de col·lisions entra en un **estat inconsistent**, provocant que el programa compleixi i incompleixi les regles simultàniament.

---

### Interacció amb objectes

Els objectes presents a la *Ghost Room* són recollibles —i utilitzables en cas que puguin ser utilitzats— en la majoria dels casos.

En el cas que un objecte aparegui adjacent a la col·lisió fantasma, aquest es torna **completament intangible**.

![Imatge d’un objecte intangible en la Ghost Room](img/the%20ghost%20room%203.png)

---

## Destrucció de límits

En tots els casos observats, la “sortida” d’aquesta habitació apareix a la part superior, fet que impedeix travessar-la per veure més enllà. Tot i això, si apareix un pic dins l’habitació, és possible avançar en altres direccions.

En avançar, les noves direccions no es carreguen correctament en el moment de la generació. A mesura que el jugador s’hi apropa, les vores del nou camí es converteixen gradualment en parets sòlides.

![Segon camí en la Ghost Room](img/the%20ghost%20room%205.png)

---

## Jugabilitat i solució

En la gran majoria dels casos, la partida es torna completament **injugable**, obligant el jugador/usuari a reiniciar el joc per obtenir una generació de mapa coherent.

Tot i així, en casos extremadament rars, és possible que la sortida es generi dins de la mateixa *Ghost Room*, forçant una excepció a una de les regles de generació:
*la distància mínima entre la sortida i l’spawn del jugador*.  
Aquest fenomen és **extraordinàriament poc freqüent**.

![Sortida en la Ghost Room](img/the%20ghost%20room%204.png)

---

## Estabilitat i naturalesa del bug

Aquest bug és completament **irreproduïble** —almenys per a un humà mitjà— ja que depèn exclusivament del RNG (sort i probabilitat). A causa de la seva baixa probabilitat d’aparició, inicialment es manifestava molt poc freqüentment.

A mesura que el projecte ha avançat, incrementant la seva complexitat, l’ús de recursos i el nombre de capes de generació, *The Ghost Room* ha començat a aparèixer amb més
freqüència.

En altres paraules, com més capes i passes de generació s’afegeixen al sistema, **major és la probabilitat que aparegui aquest estat inconsistent**.

---

## Pedaç del bug

A causa de la seva naturalesa, s’ha optat per implementar un **pedaç de mitigació** que evita l’aparició constant d’aquest bug sense eliminar-lo completament.

Aquest pedaç consisteix en una sèrie d’algoritmes que comproven si el mapa generat és **viable i jugable**, regenerant-lo en cas contrari.

Tot i que aquest sistema incrementa lleugerament el consum de recursos i el temps de generació, ha permès reduir gairebé a zero l’aparició d’aquest fenomen.  
Tal com s’ha exposat anteriorment, existeix una probabilitat molt baixa que la sortida es generi dins d’aquesta habitació, per la qual cosa encara és possible que aparegui de manera excepcional.

---

### Configuració del pedaç

El pedaç es configura al nou generador:
[SimulatorLoader.java](/maze/src/main/java/com/jairo/SimulatorLoader.java),
concretament mitjançant les següents regles:

```java
// Regla (commutador)
// Default: true
private static final boolean FORCE_REACHABLE_EXIT = true;

// Intents per complir la regla
// Default: 50
private static final int MAX_TRIES = 50;
```

La primera regla, `FORCE_REACHABLE_EXIT`, actua com a **commutador principal** del sistema de mitigació, controlant directament si el generador ha de garantir l’existència d’una sortida **reachable** des de l’spawn del jugador.

- `true`:  
  El generador força que la sortida sigui accessible des del punt d’aparició del jugador.  
  Això impedeix l’aparició d’una *Ghost Room* estàndard (sense sortida), a costa d’un **increment en el temps de generació** i un **lleuger augment del consum de recursos**.

- `false`:  
  El generador no valida l’accessibilitat de la sortida.  
  Això permet que apareguin *Ghost Rooms* com a resultat de aleatòries
  del RNG, reduint el temps de generació inicial i el consum de recursos, però incrementant el risc de generar mapes no jugables.

---

## Impacte tècnic

### Àmbit d’afectació
- Spawn inicial del jugador
- Generació global del mapa
- Objectes
- Sistema de col·lisions
- Sistema de sortides  

---

### Persistència
- **Afectació sobre partides guardades:**  
  No aplicable (el sistema no disposa de persistència de partides).

- **Persistència després de recarregar el mapa:**  
  No aplicable. El joc no disposa de cap sistema per recarregar o el mapa durant l’execució; l’única manera de sortir de l’estat afectat és reiniciar completament el joc.

---

### Estabilitat del joc
- **Crasheigs o excepcions:**  
  No se n’han detectat. El bug no provoca errors crítics a nivell d’execució.

- **Estats irrecuperables:**  
  En la gran majoria dels casos, el bug deixa la partida en un estat irrecuperable sense reiniciar, ja que el jugador queda atrapat en una zona no jugable.

---

### Impacte en la jugabilitat
- **Estat de la partida:**  
  La partida esdevé pràcticament injugable, amb una jugabilitat molt limitada o nul·la.

- **Solució disponible per al jugador:**  
  L’única solució viable és reiniciar el joc.  
  El sistema és completament volàtil i no permet guardar ni recuperar l’estat de la partida afectada.

---

### Severitat
- **Classificació interna:**  
  Low  
  (*El bug està mitigat i desactivat nativament mitjançant el sistema de validació de mapes, amb una probabilitat residual d’aparició*).
