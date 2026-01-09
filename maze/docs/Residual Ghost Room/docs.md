# Residual Ghost Room

---

## Què és?
*Residual Ghost Room* és un **Bug colateral**
que ha sigut **completament solucionat**.

Primera i única evidència:  
![Imatge de Residual Ghost Room](img/../the%20residual%20ghost%20room.png)

---

## Causa

Aquest comportament és causat pel sistema de **generació del mapa**.  
Tot i que és **impossible de reproduir intencionalment**, depèn completament
del **factor aleatori (RNG)**.

---

## Símptomes

Una cel·la aleatoria en el borde del laberint, quan es causa aquest bug, es torna una col·lisió fantasma. Aquesta col·lisió es comporta exactament igual al cas de la [Ghost Room](docs/../../The%20Ghost%20Room/docs.md), de la qual deriva el seu nom.

---

## Jugabilitat i resolució

En tots els casos es completament jugable, tot i que per desfer-se del bug, el jugador ha de reiniciar.

---

## Estabilitat i naturalesa del bug

Aquest bug és **irreproduïble** i depèn principalment del **RNG**.

---

## Pedaç / Mitigació

Donada la naturalesa del problema, s’ha optat per un **sistema que actualitza els estats de les cel·les afectades**, eliminant completament el bug.

---

## Impacte tècnic

### Àmbit d’afectació
- Generació parcial del mapa
- Objectes
- Sistema de col·lisions

---

### Persistència
- **Afectació sobre partides guardades:**  
  No aplicable (el sistema no disposa de persistència de partides).

- **Persistència després de recarregar el mapa:**  
  No aplicable. El joc no disposa de cap sistema per recarregar o el mapa durant l’execució; l’única manera de sortir de l’estat afectat reiniciar completament el joc.

---

### Estabilitat del joc
- **Crasheigs o excepcions:**  
  No se n’han detectat. El bug no provoca errors crítics a nivell d’execució.

- **Estats irrecuperables:**  
  En tots els casos, el bug no afecta a la jugabilitat pel que el estat no es veu afectat.

---

### Impacte en la jugabilitat
- **Estat de la partida:**  
  La partida no es veu afectada.

- **Solució disponible per al jugador:**  
  L’única solució viable és reiniciar el joc.  
  El sistema és completament volàtil i no permet guardar ni recuperar l’estat de la partida afectada.

- **Solució disponible per a l’usuari:**  
  [Cap / Reiniciar / Acció manual / Workaround]

---

### Severitat
- **Classificació interna:**  
  Null

- **Justificació:**  
  El bug ha sigut completament eliminat gràcies al sistema que actualitza els estats de les possibles cel·les afectades.
