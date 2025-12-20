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

</style>

# Exploració del laberint - <span class="author">[Jairo Linares](https://github.com/JJairo-16)</span>

---

## Què és?

És un joc d’exploració de laberints on el jugador es desplaça per un entorn desconegut, descobrint progressivament el mapa mentre busca la sortida. El sistema de visibilitat limita la informació disponible, fomentant l’orientació, la planificació dels moviments i l’exploració estratègica.

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

## Llicència

Aquest programa està baix la llicencia [MIT](LICENSE).
