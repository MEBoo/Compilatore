# Compilatore

1. Scanning (Analisi lessicale)
	
	Input: program, stream of chars

	Output: stream of tokens

2. Parsing (Analisi sintattica)
	
	Output: abstract Syntax Tree (AST)

3. Type Checking (Analisi semantica)

	Output: enriched AST (types/declaration)

4. Optimization (Indipendente dall'architettura)
5. Code Generation (Focus sull'architettura)

## Analisi Lessicale

Find LEXEMES and map them to TOKENS

1. Definizione dei Token tramite RE
2. Trasformo RE in DFA
3. Unisco tutti i DFA in un unico NFA con stato iniziale
4. Trasformo in unico DFA con tecnica del lookahead (guardo in avanti ma se fallisco torno l'ultimo trovato e ripristino input, si utilizza tecnica maximal match)

Tramite la sola definizione delle RE separo il cosa da come lo trovo (codice ripetitivo)

- In caso di ambiguità dare priorità ai token
- discarding whitespace
- Error: token speciale con priorità minima

## Parser

Dato lo stream dei token si genera l'AST: Syntax tree pulito dallo zucchero sintatticio (senza artefatti inutili come le parentesi).

Anche qui si separe il cosa dal come, basta definire una CFG

Noi utilizziamo ANTLR che è LL(*), algoritmo top-down con numero variabile * di lookahead.

Derivazioni canoniche sinistre, mentre il bottom-up (LR, normalmente più potente) effettua derivazioni canoniche destre

In LL le CFG devono essere:

- left fattorizzata
- non ricorsiva a sinistra
- non ambigua

## Analisi Semantica

1. Top-Down: controllare ID con le dichiarazioni attraverso la Symbol Table costruita durante la generazione del EAST (Kind, Type, Nesting level...)
2. Bottom-up: type checking

Nostro compilatore

- Scoping statico (+ vicino scope che mi racchiude e non di chi mi invoca)
- Le variabili devono essere dichiarate prima
- No multiple dichiarazione nello stesso scope, ma in scopoe diversi quella interna nasconde quella esterna
- La nostra symbol table è una lista di hashtables: ogni scope ha una hashtable e se non trovo quello che cerco risalgo fino allo scope globale
- Statically typing: verifichiamo la validitò delle operazioni eseguite sui corretti tipi durante la fase di compilazione
- Regole sottotipi: bool sottotipo di int

Covarianza: conserva la relazione di sottotipo
Controvarianza: inverte la relazione di sottotipo

In Java si possono fare i covariant array ma non sono sicuri se poi dentro faccio assegnamento non è typesafe

Nelle funzioni ho covarianza sul ritorno e controvarianza sui parametri

## CodeGeneration
Produzione del codice oggetto dopo la parte di front-end, la memoria sarà divisa tra una parte statica (codice) e da unaparte dinamica dove memorizzo strutture dati.

Si può vedere l'esecuzione come un'albero di attivazioni, lo stack rappresenta la foto delle procedure attive correnti.

Nel nostro caso tutte le procedure lasciano lo stack (che cresce verso il basso) pulito e con il risultato sulla cima.

### Layout AR

| Val 	| Info	| 	|
| - | - | - |
| CL 		| FP AR chiamante 									| |
| N° Par 	| Offset +N 										| |
| ... 		| - 												| |
| 1° Par	| Offset +1 										| |
| AL 		| FP AR dichiarazione 								| <-FP |
| RA		| Return address (nel main inizializzato fittizio) 	| |
| 1° Var/fun| Offset -2 										| |
| ... 		| - 												| |
| N° Var/fun| Offset -(N+1) 									| |

Legenda...

|  Val	| Name	| Info	|
| - | - | - |
| AR | Activation Record | Insieme di informazioni per gestire l'attivazione di una procedura, arrichitte dalle info del chiamante |
| FP | Frame Pointer     | Puntantore dell'attuale AR  in esecuzione |
| RA | Return Address     | Il punto esatto del programma dove ho fatto il salto per ripristinarlo quando torno |
| CL | Control Link    	 | Puntatore del FP del chiamante. Se f() chiama g() salterò al suo AR ma prima devo ricordare dov'era l'AR di f() così quando finisce g() tolgo la sua roba dallo stack e ritorno al AR di f()  |
| AL | Access Link 		 | Link al frame più recente dello scope in cui mi trovo, Utilizziamo scoping statico (con scoping dinamico non servirenbbe perchè basterebbe risalire la catena di CL, dei chiamanti) |

## Info Classi

- AST

	implementazione dei nodi 

- ASTGenerationSTVisitor: 

	Visito ST e genero AST producendo i nodi

	Return Node

- SymbolTableASTVisitor

	Visito AST generato, faccio la symbol table e collego le palline

	Per ogni nesting level ho un array di palline

	Entro/esco dallo scope, 

	Trovo le dichiarazioni: arrichendo la symbol table

	Trovo glio usi: collegando le palline

	Top-down

	Return void

- PrintEASTVisitor

	Print di EAST generato con le palline

 	Return void

- TypeCheckEASTVisitor

	Typechecking verificando bottom-up l'EAST

	Return TypeNode (anche dell'intero programma, tanto è funzionale)

- CodeGenerationASTvisitor

	Bottom-up
	
	Return string (codice del programma da passare alla virtual machine)

## Operatori aggiuntivi

1. Decommentare i relativi visitNote su compiler.lib/BaseASTVisitor
2. Implementare le relative classi dei nuovi nodi su AST
3. Implementare le visite per la generazione del AST su ASTGenerationSTVisitor
3. Implementare le visite per arrichirre AST con la symbol table su SymbolTableASTVisitor
4. Implementare le visite del EAST su PrintEASTVisitor
5. Implementare il typeCheck su TypeCheckEASTVisitor
6. Implementare code generation su CodeGenerationASTvisitor

## High-Order function

Passare come parametro una funzione.

```
f:bool (x:(int, int) -> bool, a:int, b:int) {
	return x(a,b)
}
```

Il problema è settare AL della funzione perchè è dinamico ed è una info del chiamante.

Faccio una closur (pacchetto di info) con:
- address x_entry
- AR dove x è dichiarato