# Compilatore

## CodeGeneration
Produzione del codice oggetto dopo la parte di front-end, la memoria sarà divisa tra una parte statica (codice) e da unaparte dinamica dove memorizzo strutture dati.
Si può vedere l'esecuzione come un'albero di attivazioni, lo stack rappresenta la foto delle procedure attive correnti.
Nel nostro caso tutte le procedure lasciano lo stack (che cresce verso il basso) pulito e con il risultato sulla cima.

### Layout AR

| CL 		| FP AR chiamante | |
| N° Par 	| Offset +N | |
| ... 		| | |
| 1° Par	| Offset +1 | |
| AL 		| FP AR dichiarazione | <- FP |
| RA		| Return address (nel main inizializzato fittizio) | |
| 1° Var/fun| Offset -2 | |
| ... 		| | |
| N° Var/fun| Offset -(N+1) | |

Legenda...

| AR | Activation Record | Insieme di informazioni per gestire l'attivazione di una procedura, arrichitte dalle info del chiamante |
| FP | Frame Pointer     | Puntantore dell'attuale AR |
| CL | Control Link    	 | Puntatore del FP del chiamante |
| AL | Access Link 		 | Link al frame più recente dello scope in cui mi trovo, Utilizziamo scoping statico |


## Operatori aggiuntivi

1. Decommentare i relativi visitNote su compiler.lib/BaseASTVisitor
