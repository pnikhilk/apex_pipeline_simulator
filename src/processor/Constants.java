package processor;

import java.util.logging.Logger;

public abstract class Constants {
	private static final Logger logger = Logger.getLogger("");

	//CONSTANT INTEGERS in PROGRAM
	public static final int PC = 4000;
	public static final int MIN_MEM = 0;
	public static final int MAX_MEM = 3999;
	public static final int ARCH_REG = 16;
	public static final int PHY_REG = 16;
	public static final int STAGE_COUNT = 8;
	public static final int SIZE = 4;
	public static final int SIZE_IQ = 12;
	public static final int SIZE_ROB = 40;
	//STAGES in PIPELINE
	public static final String STAGE_FETCH = "FETCH";
	public static final String STAGE_DRF1 = "D/R1";
	public static final String STAGE_DRF2 = "R2/Di";
	public static final String STAGE_INTFU1 = "INT_FU1";
	public static final String STAGE_INTFU2 = "INT_FU2";
	public static final String STAGE_MULFU = "MUL_FU";
	public static final String STAGE_LSFU1 = "LS_FU1";
	public static final String STAGE_LSFU2 = "LS_FU2";
	public static final String STAGE_BRFU = "BR_FU";
	public static final String STAGE_WBINT = "WB-INTFU";
	public static final String STAGE_WBLS = "WB-LSFU";
	public static final String STAGE_WBMUL = "WB-MULFU";
	
	//COMMANDS in PROGRAM
	public static final String C_INIT = "INITIALIZE";
	public static final String C_SIMULATE = "SIMULATE";
	public static final String C_SET_URF = "SET_URF_SIZE";
	public static final String C_MAP_TAB = "MAP TABLES";
	public static final String C_PRINT_IQ = "PRINT IQ";
	public static final String C_PRINT_ROB = "PRINT ROB";
	public static final String C_PRINT_URF = "PRINT URF";
	public static final String C_PRINT_MEMORY = "PRINT MEMORY";
	public static final String C_PRINT_STATS = "PRINT STATS";
	public static final String C_EXIT = "EXIT";
	//INSTRUCTIONS in PROGRAM
	public static final String NOP = "NOP";
	public static final String INST_ADD = "ADD";
	public static final String INST_SUB = "SUB";
	public static final String INST_MUL = "MUL";
	public static final String INST_MOVC = "MOVC";
	public static final String INST_AND = "AND";
	public static final String INST_OR = "OR";
	public static final String INST_EXOR = "EX-OR";
	public static final String INST_LOAD = "LOAD";
	public static final String INST_STORE = "STORE";
	public static final String INST_BZ = "BZ";
	public static final String INST_BNZ = "BNZ";
	public static final String INST_JUMP = "JUMP";
	public static final String INST_BAL = "BAL";
	public static final String INST_HALT = "HALT";
	//TYPE of INSTRUCTIONS
	public static final String TYPE_MEM = "MEM";
	public static final String TYPE_RTR = "RTR";
	public static final String TYPE_BR  = "BR";
	public static final String TYPE_MUL = "MUL";
	//TYPE of FU
	public static final String FUTYPE_RTR = "INTFU";
	public static final String FUTYPE_MEM = "LSFU";
	public static final String FUTYPE_BR = "BRFU";
	public static final String FUTYPE_MUL = "MULFU";
	
	public static Logger getLogger() {
		return logger;
	}
}
