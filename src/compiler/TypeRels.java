package compiler;

import compiler.AST.*;
import compiler.lib.*;

public class TypeRels {

	//MOD: HO
	// valuto se la funzione passata "a" è compatibile con la funzione richiesta come argomento "b" 
	// o in alternativa se la funzione che si vuole assegnare "a" è compatibile con la var "b"
	public static boolean arrowIsCompatible(ArrowTypeNode a, ArrowTypeNode b) {
		
		if(a.parlist.size() != b.parlist.size()) 
			return false;
		
		// covarianza dei return types: a.ret deve essere <= b.ret
		if(!isSubtype(a.ret, b.ret)) 
			return false;
		
		// contro-varianza sul tipo dei parametri: a.par_i >= b.par_i
		for(int i = 0; i < a.parlist.size(); i++) {
			if(!isSubtype(b.parlist.get(i), a.parlist.get(i))) 
				return false;
		}
		
		return true;
	}
	
	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	// NB: BoolTypeNode < IntTypeNode 
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode)
			return arrowIsCompatible((ArrowTypeNode)a,(ArrowTypeNode)b);
					
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}

}
