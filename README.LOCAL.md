<style>
h1 {
    position: relative;
    padding-bottom: 0.4em;
}

h1::after {
    content: "";
    position: absolute;
    left: 0;
    bottom: 0;
    width: 60px;
    height: 3px;
    background: linear-gradient(90deg, #65adff, #9ecbff);
    border-radius: 2px;
}

.author a {
    color: #9ecbff;
    font-weight: 600;
    text-decoration: none;
    transition: color .25s ease;
}

.author a:hover {
    color: #cce6ff;
    text-decoration: underline;
}

.highlight {
    color: #65adffff;
    font-weight: 600;
}

.highlight a {
    color: #65adffff;
}

.section {
    padding: 1em 1.2em;
    background: rgba(128,128,128,0.08);
    border-radius: 6px;
    margin: 1.5em 0;
}

.steps {
    display: flex;
    flex-direction: column;
    gap: 1.25em;
    margin: 1em 0;
    padding: 0;
    list-style: none;
}

.step {
    padding: 1em 1.2em;
    background: rgba(128,128,128,0.08);
    border-radius: 6px;
    border-left: 4px solid #65adff;
    transition: transform .2s ease, background .2s ease;
}

.step:hover {
    transform: translateY(-2px);
    background: rgba(128,128,128,0.12);
}

.step-title {
    font-weight: 600;
    margin-bottom: 0.35em;
}

.step-subtitle {
    font-size: 0.95em;
    font-style: italic;
    color: #becbdc;
    margin-bottom: 0.75em;
}

.step-body {
    margin: 0.2em 0 0;
}

.step-body ul {
    margin: 0.25em 0 0.25em 1.25em;
}

.step-body li {
    margin: 0.1em 0;
}

table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin: 0.75em 0 0;
  overflow: hidden;
  border-radius: 10px;
  background: rgba(128,128,128,0.06);
  border: 1px solid rgba(158, 203, 255, 0.22);
  box-shadow: 0 10px 24px rgba(0,0,0,0.18);
}

thead th {
  text-align: left;
  padding: 0.85em 1em;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: #eaf3ff;
  background: linear-gradient(90deg, rgba(101,173,255,0.35), rgba(158,203,255,0.18));
  border-bottom: 1px solid rgba(158, 203, 255, 0.22);
}

tbody td {
  padding: 0.75em 1em;
  border-bottom: 1px solid rgba(190, 203, 220, 0.14);
  color: #d7e3f2;
}

tbody tr:nth-child(odd) td {
  background: rgba(128,128,128,0.05);
}

tbody tr:hover td {
  background: rgba(101, 173, 255, 0.12);
}

tbody tr:last-child td {
  border-bottom: none;
}

tbody td:nth-child(1),
tbody td:nth-child(2) {
  width: 22%;
  text-align: center;
  font-weight: 700;
  color: #cfe6ff;
}

tbody td:nth-child(1),
tbody td:nth-child(2) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

tbody td:nth-child(1):not(:empty),
tbody td:nth-child(2):not(:empty) {
  background-image: linear-gradient(180deg, rgba(101,173,255,0.14), rgba(101,173,255,0.06));
  border-left: 1px solid rgba(158,203,255,0.14);
  border-right: 1px solid rgba(158,203,255,0.14);
}

tbody td:nth-child(3) {
  width: 56%;
}

thead th:first-child { border-top-left-radius: 10px; }
thead th:last-child  { border-top-right-radius: 10px; }
tbody tr:last-child td:first-child { border-bottom-left-radius: 10px; }
tbody tr:last-child td:last-child  { border-bottom-right-radius: 10px; }

@media (max-width: 640px) {
  table, thead, tbody, th, td, tr { display: block; }
  thead { display: none; }

  table {
    border-radius: 12px;
  }

  tbody tr {
    border-bottom: 1px solid rgba(190, 203, 220, 0.14);
  }

  tbody td {
    border: none;
    padding: 0.7em 0.9em;
  }

  tbody td:nth-child(1)::before { content: "Tecla principal"; display: block; font-size: 0.8em; color: #becbdc; margin-bottom: 0.25em; }
  tbody td:nth-child(2)::before { content: "Tecla secundària"; display: block; font-size: 0.8em; color: #becbdc; margin-bottom: 0.25em; }
  tbody td:nth-child(3)::before { content: "Acció"; display: block; font-size: 0.8em; color: #becbdc; margin-bottom: 0.25em; }

  tbody td:nth-child(1),
  tbody td:nth-child(2),
  tbody td:nth-child(3) {
    width: auto;
    text-align: left;
  }
}

.ghost-room-container {
  display: flex;
  justify-content: center;
}

.ghost-room-img {
  max-width: 70%;
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(0,0,0,0.25);
}

</style>

# Exploració del laberint - <span class="author">[Jairo Linares](https://github.com/JJairo-16)</span>

---

## Què és?

És un joc d’exploració de laberints on el jugador es desplaça per un entorn desconegut, descobrint progressivament el mapa mentre busca la sortida. El sistema de visibilitat limita la informació disponible, fomentant l’orientació, la planificació dels moviments i l’exploració estratègica.

---

## Com es juga?

L’objectiu del joc és explorar un laberint fins a trobar la sortida. Al començament, el mapa és desconegut i només es revelen les zones properes al jugador a mesura que es desplaça. Això fa que el jugador hagi d’orientar-se amb la informació limitada disponible, planificar els moviments amb cura i explorar de manera estratègica per no perdre’s i arribar a la sortida.

### Controls

<table>
  <thead>
    <tr>
      <th>Tecla principal</th>
      <th>Tecla secundària</th>
      <th>Acció</th>
    </tr>
  </thead>

  <tbody>
    <tr>
      <td >W</td>
      <td>↑</td>
      <td>Anar cap amunt</td>
    </tr>
    <tr>
      <td>A</td>
      <td>←</td>
      <td>Anar cap a l’esquerra</td>
    </tr>
    <tr>
      <td>S</td>
      <td>↓</td>
      <td>Anar cap avall</td>
    </tr>
    <tr>
      <td>D</td>
      <td>→</td>
      <td>Anar cap a la dreta</td>
    </tr>
    <tr>
      <td>E</td>
      <td>Enter</td>
      <td>Utilitzar</td>
    </tr>
    <tr>
      <td>Shift</td>
      <td></td>
      <td>Esprintar</td>
    </tr>
    <tr>
      <td>Més</td>
      <td></td>
      <td>Augmentar zoom</td>
    </tr>
    <tr>
      <td>Menys</td>
      <td></td>
      <td>Disminuir zoom</td>
    </tr>
    <tr>
      <td>Z</td>
      <td></td>
      <td>Skin anterior</td>
    </tr>
    <tr>
      <td>X</td>
      <td></td>
      <td>Següent skin</td>
    </tr>
    <tr>
      <td>1</td>
      <td></td>
      <td>Objecte anterior</td>
    </tr>
    <td>2</td>
      <td>Q</td>
      <td>Següent objecte</td>
    </tr>
    <tr>
    <td>F1</td>
    <td></td>
    <td>Alternar mostrar FPS</td>
    </tr>
  </tbody>
</table>

---

## Dependencies

- **Apache Maven**:
  
  > Eina de gestió i automatització de projectes que permet gestionar les dependències, compilar el codi i executar el projecte de manera senzilla.

- **Java 21**:

    > Versió del llenguatge de programació Java utilitzada per desenvolupar i executar l’aplicació. És necessari tenir el JDK 21 instal·lat al sistema.

---

## Frameworks

- **JavaFX**:
  
  > Framework gràfic utilitzat per construir la interfície d’usuari de l’aplicació. Proporciona components visuals, gestió d’esdeveniments i suport per a layouts responsius.

- **SLF4J**:
  
  > API de logging utilitzada per desacoblar el codi de l’aplicació de la implementació concreta de registre. Permet definir missatges de log consistents i configurar diferents nivells segons l’entorn d’execució.

---

## Com instal·lar maven

<ul class="steps">
    <li class="step">
        <div class="step-title">Descomprimir apache maven</div>
        <div class="step-subtitle">
            Descomprimir <a href="dependencies/apache-maven-3.9.12-bin.zip">apache-maven</a> i copiar el contingut en un directori accessible per altres usuaris (es recomana <code>C:\apache-maven</code>).
        </div>
    </li>
    <li class="step">
        <div class="step-title">Afegir apache-maven al path del sistema</div>
        <div class="step-subtitle">
            Crear una variable d'entorn de sistema per accedir a <code>apache-maven\bin</code> com <code>mvn</code>.
        </div>
        <div class="step-body">
            Amb PowerShell:
            <code>setx PATH "$env:PATH;C:\ruta\al\maven\bin" /M</code>
        </div>
    </li>
</ul>

---

## The Ghost Room

<span class="highlight">The Ghost Room</span> (o <em>Ghost Room</em>) és un
<strong>heisenbug conegut</strong> del projecte que s’ha decidit
<strong>no solucionar completament, només desactivar.</strong>.

<div class="section">
    Primera imatge capturada:
    <br><br>
    <div class="ghost-room-container">
        <img src="maze/docs/The%20Ghost%20Room/img/the%20ghost%20room.png" class="ghost-room-img" alt="Imatge de la Ghost Room">
    </div>
    Més informació a <a href="maze/docs/The%20Ghost%20Room/docs.md">The Ghost Room</a>

</div>



### Símptomes

En casos extremadament rars, el jugador, en lloc d'aparèixer dins del laberint,
apareix en una <strong>habitació buida</strong> amb una única sortida en forma de passadís.

Aquesta sortida és <span class="highlight">intransitable</span> a causa d'un
<strong>terra amb col·lisió fantasma</strong>, que aparentment compleix les regles
del sistema però les incompleix a nivell intern.

En altres paraules, <em>The Ghost Room</em> representa un estat en què el programa
<strong>compleix i no compleix les regles simultàniament</strong>.

### Causa

Aquest comportament és provocat pel sistema de <strong>generació del mapa</strong>.
Tot i que és <em>impossible de reproduir intencionalment</em>, depèn completament
del <strong>factor aleatori (RNG)</strong> i de condicions d'execució específiques.

### Solució

tot i existir una solució definitiva, es pot activar novament la possibilitat d'aparèixer en una Ghost Room.

Per sortir de la <em>Ghost Room</em>, només cal <strong>reiniciar el programa</strong>.

<div class="section">
    Aquest fenomen <strong>no afecta la jugabilitat normal</strong> del joc
    i s'ha decidit preservar com a <span class="highlight">comportament emergent</span>
    i element singular del projecte tot i que desactivat de forma nativa.
</div>

---

## Llicència i avisos

Aquest programa està baix la llicencia [MIT](LICENSE).

 > Aquest projecte conté una petita referència visual inspirada en còmics de ciència-ficció populars. No s'hi pretén cap afiliació ni cap avaluació.