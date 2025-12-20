# Exploració del laberint - [Jairo Linares](https://github.com/JJairo-16)

**Versió amb estil CSS (local):**  
[README complet amb estils](./README.LOCAL.md)

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

1. **Descomprimir apache maven:**
   <br>
   Descomprimir [apache-maven](dependencies/apache-maven-3.9.12-bin.zip) i copiar el contingut en un directori accessible per altres usuaris (es recomana `C:\apache-maven`).

2. **Afegir apache-maven al path del sistema:**
   <br>
   Crear una variable d'entorn de sistema per accedir a `apache-maven\bin` com `mvn`.

   Amb PowerShell:
    ```PowerShell
    setx PATH "$env:PATH;C:\ruta\al\maven\bin" /M
    ```

---

## Llicència

Aquest programa està baix la llicencia [MIT](LICENSE).
