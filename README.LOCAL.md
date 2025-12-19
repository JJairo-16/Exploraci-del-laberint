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

## Llicència

Aquest programa està baix la llicencia [MIT](LICENSE).
