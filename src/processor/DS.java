package processor;

class IQEntry implements Comparable<IQEntry>{
	private boolean allocated = false;
	private int PC = 0;
	private String typeFU = "null";
	private String op = "null";
	private int literal = 0;
	private boolean rsrc1_valid = true;
	private String rsrc1_tag = "null";
	private int rsrc1_value = 0;
	private boolean rsrc2_valid = true;
	private String rsrc2_tag = "null";
	private int rsrc2_value = 0;
	private String rdestAdd = "null";
	private int cycle = 0;
	
	public void display(){
		System.out.print(this.isAllocated() + "|");
		System.out.print(this.getTypeFU() + "|");
		System.out.print(this.getLiteral() + "|");
		System.out.print(this.isRsrc1_valid() + "|");
		System.out.print(this.getRsrc1_tag() + "|");
		System.out.print(this.getRsrc1_value() + "|");
		System.out.print(this.isRsrc2_valid() + "|");
		System.out.print(this.getRsrc2_tag() + "|");
		System.out.print(this.getRsrc2_value() + "|");
		System.out.println(this.getRdestAdd() + "|");
		
	}
	
	public int getPC() {
		return PC;
	}

	public void setPC(int pC) {
		PC = pC;
	}

	public boolean isAllocated() {
		return allocated;
	}
	public void setAllocated(boolean allocated) {
		this.allocated = allocated;
	}
	public String getTypeFU() {
		return typeFU;
	}
	public void setTypeFU(String typeFU) {
		this.typeFU = typeFU;
	}
	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public int getLiteral() {
		return literal;
	}
	public void setLiteral(int literal) {
		this.literal = literal;
	}
	public boolean isRsrc1_valid() {
		return rsrc1_valid;
	}
	public void setRsrc1_valid(boolean rsrc1_valid) {
		this.rsrc1_valid = rsrc1_valid;
	}
	public String getRsrc1_tag() {
		return rsrc1_tag;
	}
	public void setRsrc1_tag(String rsrc1_tag) {
		this.rsrc1_tag = rsrc1_tag;
	}
	public int getRsrc1_value() {
		return rsrc1_value;
	}
	public void setRsrc1_value(int rsrc1_value) {
		this.rsrc1_value = rsrc1_value;
	}
	public boolean isRsrc2_valid() {
		return rsrc2_valid;
	}
	public void setRsrc2_valid(boolean rsrc2_valid) {
		this.rsrc2_valid = rsrc2_valid;
	}
	public String getRsrc2_tag() {
		return rsrc2_tag;
	}
	public void setRsrc2_tag(String rsrc2_tag) {
		this.rsrc2_tag = rsrc2_tag;
	}
	public int getRsrc2_value() {
		return rsrc2_value;
	}
	public void setRsrc2_value(int rsrc2_value) {
		this.rsrc2_value = rsrc2_value;
	}
	public String getRdestAdd() {
		return rdestAdd;
	}
	public void setRdestAdd(String rdestAdd) {
		this.rdestAdd = rdestAdd;
	}

	public int getCycle() {
		return cycle;
	}

	public void setCycle(int cycle) {
		this.cycle = cycle;
	}

	@Override
	public int compareTo(IQEntry o) {
		int cycle = o.getCycle();
		return this.cycle - cycle;
	}
	
}

class ROBEntry {
	private int pcValue;
	private String instrType;
	private int result;
	private boolean zeroFlag;
	private String rDestArch;
	private String rDestPhyAdd;
	private boolean staus = false;
	private String  other;
	
	public void  display(){
		System.out.print(this.getPcValue() + "|");
		System.out.print(this.getInstrType() + "|");
		System.out.print(this.getResult() + "|");
		System.out.print(this.isZeroFlag() + "|");
		System.out.print(this.getrDestArch() + "|");
		System.out.print(this.getrDestPhyAdd() + "|");
		System.out.println(this.isStaus());
	}
	
	public int getPcValue() {
		return pcValue;
	}
	public void setPcValue(int pcValue) {
		this.pcValue = pcValue;
	}
	public String getInstrType() {
		return instrType;
	}
	public void setInstrType(String instrType) {
		this.instrType = instrType;
	}
	public int getResult() {
		return result;
	}
	public void setResult(int result) {
		this.result = result;
	}
	public boolean isZeroFlag() {
		return zeroFlag;
	}
	public void setZeroFlag(boolean zeroFlag) {
		this.zeroFlag = zeroFlag;
	}
	public String getrDestArch() {
		return rDestArch;
	}
	public void setrDestArch(String rDestArch) {
		this.rDestArch = rDestArch;
	}
	public String getrDestPhyAdd() {
		return rDestPhyAdd;
	}
	public void setrDestPhyAdd(String rDestPhyAdd) {
		this.rDestPhyAdd = rDestPhyAdd;
	}
	public boolean isStaus() {
		return staus;
	}
	public void setStaus(boolean staus) {
		this.staus = staus;
	}

	public String getOther() {
		return other;
	}

	public void setOther(String other) {
		this.other = other;
	}
}

class PR{
	String phyRegName;
	boolean allocated;
	String zeroFlag;
	
	public String isZeroFlag() {
		return zeroFlag;
	}
	public void setZeroFlag(String zeroFlag) {
		this.zeroFlag = zeroFlag;
	}
	public PR(String reg, boolean b, String z) {
		this.phyRegName = reg;
		this.allocated = b;
		this.zeroFlag = z;
	}
	public String getPhyRegName() {
		return phyRegName;
	}
	public void setPhyRegName(String phyRegName) {
		this.phyRegName = phyRegName;
	}
	public boolean isAllocated() {
		return allocated;
	}
	public void setAllocated(boolean allocated) {
		this.allocated = allocated;
	}
}
