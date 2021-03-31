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

ANTLR gestisce una grammatica estesa con EBNF (RE dentro la grammatica) ma è del tutto equivalente con una CFG

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

	Su Java il dynamic binding non c'è sui parametri ma solo sui soggetti

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

Per prima cosa era già stata aggiunta la variabile non-terminale hotype sulla GFG che può essere un type base (bool, int) oppure una arrow (function)

Il problema è settare AL della funzione perchè è dinamico ed è una info del chiamante.

Faccio una closur (pacchetto di info) con:
- AR dove x è dichiarato -> [a offset messo in symbol table] indirizzo FP di AR dichiarazione funzione 
- address x_entry -> [a offset messo in symbol table-1] indirizzo funzione per invocere il suo codice

Il layout AR rimane invariato ma ora qualsiasi ID con tipo funzionale occupa spazio doppio

1. SU AST il nodo ArrowTypeNode esisteva già e veniva utilizzato per le funzioni, ora lo usiamo anche per le funzioni passate per argomento
2. Implementata la visita "visitArrow" su ASTGenerationSTVisitor

	- i parametri non sono parNode come su funzione ma semplici typeNode perchè la sintassi è x:(int,int)->int e non x(a:int,b:int)->int

3. Modificata la visita di FunNode e VarNode per gestire gli offset doppi nel caso di arrowTypeNode su "SymbolTableEASTVisitor"

	- il decOffset su funNode va decrementato sempre di 2 perchè ora occupa 2 spazi (sempre)
	- il parOffset su funNode viene pre incrementato perchè lo stack cresce verso il basso, quindi l'offset a cui assegno la entry è quello successivo e quando creo AR inizio a scrivere da n-esimo par in giù
	- il decOffset su varNode va post decrementato perchè lo stack cresce verso il basso, quindi prima segno il punto in cui mettere l'indirizzo e poi alloco uno spazio. quando creo AR scrivo da 1 dec in su

4. Su TypeCheckEASTVisitor permetto che gli idNode siano ArrowType mentre blocco questa possibilità su ==, <=, >= (non ha senso)

5. Aggiunto controllo su arrowtype in TypeRels per il typechecking (covarianza ritorno, controvarianza parametri)

	- covarianza: permette di usare un tipo più derivato di quello specificato, serve a preservare la relazione di sottotipi
	- controvarianza: permette di utilizzare un tipo più generico di quello specificato

	```
	Class Animal { eat() }
	Class Bird extends Animal { fly() }

	Eat(a:Animal) { a.eat() }
	Fly(b:Bird) { b.fly() }

	AnimalDelegate(a:Animal) 
	BirdDelegate(b:Bird) 

	BirdDelegate d1 = Eat
	foo(new Bird())
	// OK, Eat usa sovratipo animal e lo posso passare dvoe è richiesto un bird
	perchè lancia la funzione mangia, tutti gli animali mangiano

	AnimalDelegate d2 = Fly
	foo(new Animal())
	// ERROR, non posso passare una funzione che accetta sottoclasse ad una con la sovraclasse, non tutti gli animali volano
	```

	```
	z:(mezzo)->car
	a:(car)->car
	b:(ford)->car

	fun pippo:car( c:(car)->car ){
		c(car)
	}

	pippo(z) OK
	pippo(a) OK
	pippo(b) NO
	```
6. CodeGenerationASTvisitor

	- FunNode (pulisco bene la casa nel caso di par/var arrowType) e faccio la closure
	- CallNode (la chiamata deve usare come access link il puntatore dell'AR impacchettato nell'ID in chiamata)
	- IdNode (tengo in considerazione la closure)