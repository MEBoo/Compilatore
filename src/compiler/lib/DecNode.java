package compiler.lib;

public abstract class DecNode extends Node {
	
	public String id; 	//MOD - NEW SYMBOL TABLE MANAGEMENT
	protected TypeNode type;
		
	public TypeNode getType() {return type;}

}
