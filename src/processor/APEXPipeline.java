package processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;

public class APEXPipeline {
	private class Stages {		
		private boolean isHalt = false;
		private boolean branchDispatched = false;
		private boolean branchFlush = false;
		private boolean branchDecision = false;
		private boolean halt = false;
		private Register wakeUp1 = null; //INTFU
		private Register wakeUp2 = null; //MULFU
		private Register wakeUp3  = null; //LSFU
		private Register wakeUp4 = null;
		private Register commit = null;
		private Register tempMEM = null;
		int count = 0;
		private int dispatchStall = 0;
		private int loadCount = 0;
		private int storeCount = 0;
		private int issueStall = 0;

		/*Decode
		 * This method performs following tasks:
		 * 1. Taking instruction from Fetch stage
		 * 2. Reads Physical registers from RAT
		 * 3. Renames Source registers
		 * */
		public void decode() {
			if (pipeline.get(Constants.STAGE_DRF1) == null && !branchFlush && !halt) {
				if (pipeline.get(Constants.STAGE_FETCH) != null) {
					pipeline.put(Constants.STAGE_DRF1, pipeline.get(Constants.STAGE_FETCH));
					pipeline.put(Constants.STAGE_FETCH, null);

					// Check for dependency
					Instruction instr = pipeline.get(Constants.STAGE_DRF1);
					decodeInstruction(instr);
					Constants.getLogger().log(Level.INFO, "Decoded instruction " + instr.getInstructionInfo());
				}
			}
		}

		/*Dispatch
		 * 1. Works as Rename2/Dispatch stage
		 * 2. Checks for register dependency
		 * 3. Renames Destination Register 
		 * */
		public void dispatch(){
			if(pipeline.get(Constants.STAGE_DRF2) == null && !branchFlush && !halt){
				if(pipeline.get(Constants.STAGE_DRF1) != null){
					pipeline.put(Constants.STAGE_DRF2, pipeline.get(Constants.STAGE_DRF1));
					pipeline.put(Constants.STAGE_DRF1, null);

					Instruction instr = pipeline.get(Constants.STAGE_DRF2);
					if (!checkDependency(instr)) {
						instr.setDependency(false);
					} else {
						isRegisterUpdated(instr);
						Constants.getLogger().log(Level.WARNING, "Dependency detected.");
						Constants.getLogger().log(Level.INFO, "Instruction " + instr.getInstructionInfo());
						instr.setDependency(true);
					}
					
					if(instr.getOpCode().equals(Constants.INST_BAL)){
						instr.getrDest().setRegName("X");
					}

					//Renaming rDest while dispatching
					if(instr.getrDest().getRegName() != null){
						String reg = freeList.poll();
						String rDest = instr.getrDest().getRegName();
						if(RAT.get(rDest)!= null && !RAT.get(rDest).getPhyRegName().isEmpty()){
							regFile.PRF.get(RAT.get(rDest).getPhyRegName()).setAllocated(false);
							String phyReg = regFile.PRF.get(RAT.get(rDest).getPhyRegName()).getRegName();
							freePRF(rDest, phyReg);

						}
						RAT.put(rDest, new PR(reg, true, reg));
						regFile.PRF.get(reg).setAllocated(true);
						regFile.PRF.get(reg).setValid(false);
						instr.getrDest().setRegName(reg);
					}
				}	
			}
			else{
				Instruction instr = pipeline.get(Constants.STAGE_DRF2);
				if(instr!=null)
					isRegisterUpdated(instr);
			}
		}

		/*Executes:
		 * 1. This method performs task of execution.
		 * 2. Contains INTFU - 2cycles, LSFU 2cycles, MULFU - 4cycle non-pipelined and BRFU
		 * 3. If respective FU is free instrcution is copied to FU and IQEntry for the instruction is removed.
		 * */
		public void execute() {
			// execution logic
			//INTFU
			executeINTFU();
			//MULFU
			executeMULFU();
			//LSFU
			executeLSFU();

			/**Logic for instructions after issued
			 * */
			IQEntry[] readyInstructions = getReadyInstrucions();
			Instruction instr = null;
			IQEntry entry = null;

			boolean intStall = true;
			boolean lsStall = true;
			boolean mulStall = true;
			boolean brStall = true;

			for(int index = 0; index < readyInstructions.length && readyInstructions[index] != null; index++){
				entry = readyInstructions[index];

				switch(entry.getTypeFU()){
				case Constants.FUTYPE_RTR:
					instr = pipeline.get(Constants.STAGE_INTFU1); 
					if(instr == null){
						instr = new Instruction(entry.getOp());
						instr.setPC(entry.getPC());
						instr.setLiteral(entry.getLiteral());
						instr.getrDest().setRegName(entry.getRdestAdd());
						instr.getrSrc1().setRegName(entry.getRsrc1_tag());
						instr.getrSrc1().setValue(entry.getRsrc1_value());
						instr.getrSrc2().setRegName(entry.getRsrc2_tag());
						instr.getrSrc2().setValue(entry.getRsrc2_value());
						pipeline.put(Constants.STAGE_INTFU1, instr);

						Constants.getLogger().log(Level.INFO, "INTFU1 instruction " + instr.getInstructionInfo());

						issueQueue.remove(entry);
						readyInstructions[index] = null;
						intStall = false;
					}
					break;
				case Constants.FUTYPE_MUL:
					instr = pipeline.get(Constants.STAGE_MULFU); 
					if(instr == null){
						instr = new Instruction(entry.getOp());
						instr.getrDest().setRegName(entry.getRdestAdd());
						instr.getrSrc1().setRegName(entry.getRsrc1_tag());
						instr.getrSrc1().setValue(entry.getRsrc1_value());
						instr.getrSrc2().setRegName(entry.getRsrc2_tag());
						instr.getrSrc2().setValue(entry.getRsrc2_value());
						pipeline.put(Constants.STAGE_MULFU, instr);

						Constants.getLogger().log(Level.INFO, "MULFU instruction " + instr.getInstructionInfo());
						count = 1;
						issueQueue.remove(entry);
						readyInstructions[index] = null;
						mulStall = false;
					}
					break;
				case Constants.FUTYPE_MEM:
					instr = pipeline.get(Constants.STAGE_LSFU1);
					boolean flag = true;
					//Check if entry dont have any stalled LOAD/STORE before
					Object obj[] = issueQueue.toArray();
					for(Object o : obj){
						IQEntry e = (IQEntry) o;
						if(entry == e){
							break;
						}
						else if(e.getTypeFU().equals(Constants.FUTYPE_MEM)){
							flag = false;
						}
					}
					if(instr == null && flag){
						instr = new Instruction(entry.getOp());
						instr.setPC(entry.getPC());
						instr.setLiteral(entry.getLiteral());
						instr.getrDest().setRegName(entry.getRdestAdd());
						instr.getrSrc1().setRegName(entry.getRsrc1_tag());
						instr.getrSrc1().setValue(entry.getRsrc1_value());
						instr.getrSrc2().setRegName(entry.getRsrc2_tag());
						instr.getrSrc2().setValue(entry.getRsrc2_value());
						pipeline.put(Constants.STAGE_LSFU1, instr);

						Constants.getLogger().log(Level.INFO, "LSFU1 instruction " + instr.getInstructionInfo());

						issueQueue.remove(entry);
						readyInstructions[index] = null;
						int result;
						if(instr.getOpCode().equals(Constants.INST_LOAD)){
							result = instr.getrSrc1().getValue() + instr.getLiteral();
						}
						else{
							result = instr.getrSrc2().getValue() + instr.getLiteral();
						}
						tempMEM = new Register("Temp", result, currCycle, instr.getPC());
						lsStall = false;
					}
					break;
				case Constants.FUTYPE_BR:
					//BRFU
					instr = pipeline.get(Constants.STAGE_BRFU); 
					if(instr == null){
						instr = new Instruction(entry.getOp());
						instr.setPC(entry.getPC());
						instr.setLiteral(entry.getLiteral());
						pipeline.put(Constants.STAGE_BRFU, instr);

						Constants.getLogger().log(Level.INFO, "BRFU instruction " + instr.getInstructionInfo());

						issueQueue.remove(entry);
						readyInstructions[index] = null;
						branchDispatched = false;
						executeBRFU(entry);
					}
					brStall = false;
					break;
				}
			}

			if(intStall && brStall && mulStall && lsStall && entry != null){
				issueStall++;
				Constants.getLogger().log(Level.INFO, "Instruction stalled in IQ " + instr.getInstructionInfo());
			}
		}

		private void executeMULFU() {
			Instruction instr = pipeline.get(Constants.STAGE_MULFU);
			if(instr != null && count == 1){
				instr.setZeroFlag(false);
				int op1 = instr.getrSrc1().getValue();
				int op2 = instr.getrSrc2().getValue();
				int result = op1 * op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				Constants.getLogger().log(Level.INFO, "MULFU - instruction cycle 2");
				count++;
			}
			else if(instr != null && count < 4){
				Constants.getLogger().log(Level.INFO, "MULFU - instruction cycle " + count);
				count++;
			}
			else if(instr != null && count == 4){
				Object[] rob1 = rob.toArray();
				for(Object obj : rob1){
					ROBEntry entry = (ROBEntry) obj;
					if(entry.getrDestPhyAdd() != null && entry.getrDestPhyAdd().equals(instr.getrDest().getRegName())){
						entry.setResult(instr.getrDest().getValue());
						entry.setZeroFlag(instr.isZeroFlag());
						entry.setStaus(true);
						wakeUp2 = instr.getrDest();
						wakeUp2.setZeroFlag(instr.isZeroFlag());
						wakeUp2.setPc(instr.getPC());
						branchDecision = instr.isZeroFlag();
						Constants.getLogger().log(Level.INFO, "MULFU - instruction cycle " + count);
						break;
					}
				}
			}
			
		}

		private void executeBRFU(IQEntry entry) {
			switch (entry.getOp()) {
			case Constants.INST_BZ:
				Object[] robA = rob.toArray();
				for(Object obj : robA){
					ROBEntry e = (ROBEntry) obj;
					if(e.getPcValue() == entry.getPC()){
						if (!branchDecision) {	
							e.setStaus(true);
							e.setOther("N");
							branchFlush = false;
							break;
						}		
						else{
							int result = entry.getPC() + entry.getLiteral();
							setCurrPC(result);
							e.setStaus(true);
							e.setOther("T");
							branchFlush = true;
							isHalt = true;
							break;
						}
					}
				}
				break;
			case Constants.INST_BNZ:
				Object[] robB = rob.toArray();
				for(Object obj : robB){
					ROBEntry e = (ROBEntry) obj;
					if(e.getPcValue() == entry.getPC()){
						if (branchDecision) {	
							e.setStaus(true);
							e.setOther("N");
							branchFlush = false;
							break;
						}		
						else{
							int result = entry.getPC() + entry.getLiteral();
							setCurrPC(result);
							e.setStaus(true);
							e.setOther("T");
							branchFlush = true;
							break;
						}
					}
				}
				break;
			case Constants.INST_BAL:
				Object[] robC = rob.toArray();
				for(Object obj : robC){
					ROBEntry e = (ROBEntry) obj;
					if(e.getPcValue() == entry.getPC()){
						int result = entry.getRsrc1_value() + entry.getLiteral();
						setCurrPC(result);
						e.setResult(e.getPcValue()+Constants.SIZE);
						e.setStaus(true);
						e.setOther("T");
						branchFlush = true;
						break;
					}
				}
				break;
			case Constants.INST_JUMP:
				Object[] robJ = rob.toArray();
				for(Object obj : robJ){
					ROBEntry e = (ROBEntry) obj;
					if(e.getPcValue() == entry.getPC()){
						int result = entry.getRsrc1_value() + entry.getLiteral();
						setCurrPC(result);
						e.setStaus(true);
						e.setOther("T");
						branchFlush = true;
						break;
					}
				}
				break;
			case Constants.INST_HALT:
				Object[] robH = rob.toArray();
				for(Object obj : robH){
					ROBEntry e = (ROBEntry) obj;
					if(e.getPcValue() == entry.getPC()){
							e.setStaus(true);
							e.setOther("N");
							isHalt = true;
							branchFlush = false;
							break;
					}
				}
				break;
			}
		}

		private void executeLSFU() {
			if(pipeline.get(Constants.STAGE_LSFU2) == null){
				Instruction instr = pipeline.get(Constants.STAGE_LSFU1);
				if(instr != null){
					pipeline.put(Constants.STAGE_LSFU2, instr);
					pipeline.put(Constants.STAGE_LSFU1, null);

					if(rob.peek().getPcValue() == instr.getPC()){

						if (instr.getOpCode().equals(Constants.INST_LOAD)) {
							instr.getrDest().setValue(dataMemory.getMemory(tempMEM.getValue()));

							rob.peek().setResult(instr.getrDest().getValue());
							rob.peek().setStaus(true);

							wakeUp3 = instr.getrDest();
							wakeUp3.setPc(instr.getPC());
							Constants.getLogger().log(Level.INFO, "LOAD instruction in LSFU2, Data loaded from memory location " + tempMEM.getValue());
						} else {
							dataMemory.setMemory(tempMEM.getValue(), instr.getrSrc1().getValue());
							rob.poll();
							storeCount++;
							pipeline.put(Constants.STAGE_LSFU2, null);
							Constants.getLogger().log(Level.INFO, "STORE instruction in LSFU2, Data stored memory location " + tempMEM.getValue());
						}

					}
				}
			}
		}

		private void executeINTFU() {
			// Logic for INTFU
			if(pipeline.get(Constants.STAGE_INTFU2) == null){
				Instruction instr = pipeline.get(Constants.STAGE_INTFU1);
				if(instr != null){
					pipeline.put(Constants.STAGE_INTFU2, instr);
					pipeline.put(Constants.STAGE_INTFU1, null);

					Constants.getLogger().log(Level.INFO, "INTFU2 instruction " + instr.getInstructionInfo());
					branchDecision = false;
					executeRTR(instr);
					Object[] rob1 = rob.toArray();
					for(Object obj : rob1){
						ROBEntry entry = (ROBEntry) obj;
						if(entry.getrDestPhyAdd() != null && entry.getrDestPhyAdd().equals(instr.getrDest().getRegName())){
							// for MOVC may be change
							if(instr.getOpCode().equals(Constants.INST_MOVC)){
								entry.setResult(instr.getLiteral());
								instr.getrDest().setValue(instr.getLiteral());
							}
							else{
								entry.setResult(instr.getrDest().getValue());
							}
							entry.setZeroFlag(instr.isZeroFlag());
							entry.setStaus(true);
							wakeUp1 = instr.getrDest();
							wakeUp1.setZeroFlag(instr.isZeroFlag());
							wakeUp1.setPc(instr.getPC());
							branchDecision = instr.isZeroFlag();
							break;
						}
					}

				}
			}

		}

		/*SendWakeUP
		 * Sends wakeup signal to IQ
		 * */
		private void sendWakeup(Register rDest) {
			//send Wakeup signal
			String tag = rDest.getRegName();
			int value = rDest.getValue();
			Object[] queue = issueQueue.toArray();

			for(Object obj : queue){
				IQEntry entry = (IQEntry) obj;

				if(entry.getRsrc1_tag().equals(tag) && !entry.isRsrc1_valid()){
					entry.setRsrc1_value(value);
					entry.setRsrc1_valid(true);
				}
				if(entry.getRsrc2_tag().equals(tag) && !entry.isRsrc2_valid()){
					entry.setRsrc2_value(value);
					entry.setRsrc2_valid(true);
				}
			}
		}

		/*GetReadyInstruction:
		 * Used as selection logic
		 * Selects ready instructions from IQ in FIFO order
		 * */
		private IQEntry[] getReadyInstrucions() {
			// getting ready/waked-up instructions
			IQEntry[] temp = new IQEntry[12];
			int index = 0;

			for (IQEntry entry : issueQueue){
				if(entry.isRsrc1_valid() && entry.isRsrc2_valid()){
					temp[index++] = entry;
				}
			}
			return temp;
		}

		/*AddTOROB:
		 * Add ROB entry for instruction
		 * */
		private void addToROB(Instruction instruction) {
			// Insert ROB entry
			ROBEntry entry = new ROBEntry();
			String rDest = "";

			entry.setPcValue(instruction.getPC());
			entry.setInstrType(instruction.getType());
			entry.setResult(0);
			entry.setZeroFlag(false);
			for(Map.Entry<String, PR> e : RAT.entrySet()){
				if(e.getValue().getPhyRegName().equals(instruction.getrDest().getRegName())){
					rDest = e.getKey();
					break;
				}
			}
			entry.setrDestArch(rDest);
			entry.setrDestPhyAdd(instruction.getrDest().getRegName());
			rob.add(entry);

		}

		/*AddToIQ:
		 * Adds IQ Entry for Instruction
		 * */
		private void addToIQ(Instruction instruction) {
			// Insert IQ entry
			IQEntry entry = new IQEntry();

			entry.setAllocated(true);
			entry.setPC(instruction.getPC());
			switch(instruction.getType()){
			case Constants.TYPE_RTR:
				entry.setTypeFU(Constants.FUTYPE_RTR);
				break;

			case Constants.TYPE_MUL:
				entry.setTypeFU(Constants.FUTYPE_MUL);
				break;

			case Constants.TYPE_MEM:
				entry.setTypeFU(Constants.FUTYPE_MEM);
				break;

			case Constants.TYPE_BR:
				entry.setTypeFU(Constants.FUTYPE_BR);
				break;
			}
			entry.setOp(instruction.getOpCode());
			entry.setLiteral(instruction.getLiteral());
			if(instruction.getrSrc1().getRegName() != null){
				entry.setRsrc1_valid(!instruction.isSrc1Dependency());
				entry.setRsrc1_tag(instruction.getrSrc1().getRegName());
				entry.setRsrc1_value(instruction.getrSrc1().getValue());
			}
			if(instruction.getrSrc2().getRegName() != null){
				entry.setRsrc2_valid(!instruction.isSrc2Dependency());
				entry.setRsrc2_tag(instruction.getrSrc2().getRegName());
				entry.setRsrc2_value(instruction.getrSrc2().getValue());
			}
			entry.setRdestAdd(instruction.getrDest().getRegName());
			entry.setCycle(currCycle);
			//	entry.display();
			issueQueue.add(entry);
		}

		/*
		 * This method works as Fetch stage in pipeline.
		 * It fetches an instruction form I-Cache and updates PC.
		 */
		public void fetch() {
			if (pipeline.get(Constants.STAGE_FETCH) == null && !branchFlush && !halt && iCache.get(currPC) != null) {
				Instruction instr = new Instruction(iCache.get(currPC));
				currPC += Constants.SIZE;
				if (instr != null) {
					pipeline.put(Constants.STAGE_FETCH, instr);

					Constants.getLogger().log(Level.INFO, "Fetched instruction " + instr.getInstructionInfo());
				}
			}
		}

		/*
		 * This method checks for dependency between instructions.
		 * If no dependency, then fetch register contents from Register File.
		 * */
		private boolean checkDependency(Instruction instr) {
			boolean isDependency = false;
			if (instr != null) {
				switch (instr.getOpCode()) {
				case Constants.INST_ADD:
				case Constants.INST_SUB:
				case Constants.INST_MUL:
				case Constants.INST_AND:
				case Constants.INST_OR:
				case Constants.INST_EXOR:
					if (!regFile.PRF.get(instr.getrSrc1().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc1().getRegName())){
							instr.getrSrc1().setAllocated(false);
							instr.getrSrc1().setValue(commit.getValue());
						}
						else{
							instr.setSrc1Dependency(true);	
						}
						instr.getrSrc1().setPc(regFile.PRF.get(instr.getrSrc1().getRegName()).getPc());
					}
					else{
						instr.getrSrc1().setValue(regFile.PRF.get(instr.getrSrc1().getRegName()).getValue());
					}
					if (!regFile.PRF.get(instr.getrSrc2().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc2().getRegName())){
							instr.getrSrc2().setAllocated(false);
							instr.getrSrc2().setValue(commit.getValue());
						}
						else{
							instr.setSrc2Dependency(true);	
						}
						instr.getrSrc2().setPc(regFile.PRF.get(instr.getrSrc2().getRegName()).getPc());
					}
					else{
						instr.getrSrc2().setValue(regFile.PRF.get(instr.getrSrc2().getRegName()).getValue());
					}
					break;
				case Constants.INST_LOAD:
					if (!regFile.PRF.get(instr.getrSrc1().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc1().getRegName())){
							instr.getrSrc1().setAllocated(false);
							instr.getrSrc1().setValue(commit.getValue());
						}
						else{
							instr.setSrc1Dependency(true);	
						}
						instr.getrSrc1().setPc(regFile.PRF.get(instr.getrSrc1().getRegName()).getPc());
					}
					else{
						instr.getrSrc1().setValue(regFile.PRF.get(instr.getrSrc1().getRegName()).getValue());
					}

					break;
				case Constants.INST_STORE:
					if (!regFile.PRF.get(instr.getrSrc1().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc1().getRegName())){
							instr.getrSrc1().setAllocated(false);
							instr.getrSrc1().setValue(commit.getValue());
						}
						else{
							instr.setSrc1Dependency(true);	
						}
						instr.getrSrc1().setPc(regFile.PRF.get(instr.getrSrc1().getRegName()).getPc());
					}
					else{
						instr.getrSrc1().setValue(regFile.PRF.get(instr.getrSrc1().getRegName()).getValue());
					}
					if (!regFile.PRF.get(instr.getrSrc2().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc2().getRegName())){
							instr.getrSrc2().setAllocated(false);
							instr.getrSrc2().setValue(commit.getValue());
						}
						else{
							instr.setSrc2Dependency(true);	
						}
						instr.getrSrc2().setPc(regFile.PRF.get(instr.getrSrc2().getRegName()).getPc());
					}
					else{
						instr.getrSrc2().setValue(regFile.PRF.get(instr.getrSrc2().getRegName()).getValue());
					}
					break;
				case Constants.INST_BNZ:
				case Constants.INST_BZ:
					//Check if previous instruction is in INTFU2 stage, if no instruction in INTFU2 check in INTFU1 stage
					if (!iCache.get(instr.getPC()-Constants.SIZE).getrDest().isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc1().getRegName())){
							instr.getrSrc1().setAllocated(false);
							instr.getrSrc1().setValue(commit.getValue());
						}
						else{
							instr.setSrc1Dependency(true);	
							instr.getrSrc1().setRegName(RAT.get(iCache.get(instr.getPC()-Constants.SIZE).getrDest().getRegName()).getPhyRegName());
						}
					}
					break;
				case Constants.INST_BAL:
					if (!regFile.PRF.get(instr.getrSrc1().getRegName()).isValid()) {
						isDependency = true;
						if(commit != null && commit.getRegName().equals(instr.getrSrc1().getRegName())){
							instr.getrSrc1().setAllocated(false);
							instr.getrSrc1().setValue(commit.getValue());
						}
						else{
							instr.setSrc1Dependency(true);	
						}
						instr.getrSrc1().setPc(regFile.PRF.get(instr.getrSrc1().getRegName()).getPc());
					}
					else{
						instr.getrSrc1().setValue(regFile.PRF.get(instr.getrSrc1().getRegName()).getValue());
					}
					break;
				}
			}

			return isDependency;
		}

		/* This method decodes the instruction and reads required source register values  from register File.
		 * This operation is done in Decode stage.
		 * */
		private void decodeInstruction(Instruction instr) {
			Register rSrc1 = instr.getrSrc1();
			Register rSrc2 = instr.getrSrc2();

			if(rSrc1.getRegName() != null && (rSrc1.getRegName().contains("R") || rSrc1.getRegName().contains("X"))){
				String phyReg = RAT.get(rSrc1.getRegName()).getPhyRegName();
				//replace architecture register if physical register is allocated
				int value = regFile.PRF.get(phyReg).getValue();
				rSrc1.setRegName(phyReg);
				rSrc1.setValue(value); 
			}

			if(rSrc2.getRegName() != null && rSrc2.getRegName().contains("R")){
				String phyReg = RAT.get(rSrc2.getRegName()).getPhyRegName();
				//replace architecture register if physical register is allocated
				int value = regFile.PRF.get(phyReg).getValue();
				rSrc2.setRegName(phyReg);
				rSrc2.setValue(value);
			}
		}

		/*
		 * This method does calculations required for arithmetic instructions.
		 * */
		private void executeRTR(Instruction instr) {
			int result;
			int op1, op2;
			instr.setZeroFlag(false);
			switch (instr.getOpCode()) {
			case Constants.INST_ADD:
				op1 = instr.getrSrc1().getValue();
				op2 = instr.getrSrc2().getValue();
				result = op1 + op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				break;
			case Constants.INST_SUB:
				op1 = instr.getrSrc1().getValue();
				op2 = instr.getrSrc2().getValue();
				result = op1 - op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				break;
			case Constants.INST_MOVC:
				result = instr.getrDest().getValue() + 0;
				instr.getrDest().setValue(result);
				break;
			case Constants.INST_AND:
				op1 = instr.getrSrc1().getValue();
				op2 = instr.getrSrc2().getValue();
				result = op1 & op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				break;
			case Constants.INST_OR:
				op1 = instr.getrSrc1().getValue();
				op2 = instr.getrSrc2().getValue();
				result = op1 | op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				break;
			case Constants.INST_EXOR:
				op1 = instr.getrSrc1().getValue();
				op2 = instr.getrSrc2().getValue();
				result = op1 ^ op2;
				if (result == 0) {
					instr.setZeroFlag(true);
				}
				instr.getrDest().setValue(result);
				break;
			}
		}

		/*Get previous instructions
		 * private Instruction[] getPreviousInstructions() {
			int index = 0;
			int max = (currPC - Constants.PC - Constants.SIZE)/ Constants.SIZE;
			int tempPC = currPC - (Constants.SIZE * 2);
			Instruction[] temp = new Instruction[5];
			while(max > 0 && index < temp.length){
				temp[index++] = iCache.get(tempPC);
				max -= 1;
				tempPC -= Constants.SIZE;
			}
			return temp;
		}*/

		/*
		 * This method checks if register is forwarded for dependent instructions.
		 * */
		private boolean isRegisterUpdated(Instruction instr) {
			boolean flag = false;
			if (wakeUp1 != null) {
				if (instr.getrSrc1().getRegName() != null && instr.getrSrc1().getRegName().equals(wakeUp1.getRegName())) {
					//	if(instr.getrSrc1().getPc() == wakeUp1.getPc()){
					instr.getrSrc1().setValue(wakeUp1.getValue());
					instr.setSrc1Dependency(false);
					flag = true;
					//}
				}
				if (instr.getrSrc2().getRegName() != null)
					if (instr.getrSrc2().getRegName().equals(wakeUp1.getRegName())) {
						//	if(instr.getrSrc2().getPc() == wakeUp1.getPc()){
						instr.getrSrc2().setValue(wakeUp1.getValue());
						instr.setSrc2Dependency(false);
						flag = true;
						//	}
					}
			}
			if (wakeUp2 != null) {
				if (instr.getrSrc1().getRegName().equals(wakeUp2.getRegName())) {
					if(instr.getrSrc1().getPc() == wakeUp2.getPc()){
						instr.getrSrc1().setValue(wakeUp2.getValue());
						instr.setSrc1Dependency(false);
						flag = true;
					}
				}
				if (instr.getrSrc2().getRegName() != null)
					if (instr.getrSrc2().getRegName().equals(wakeUp2.getRegName())) {
						//	if(instr.getrSrc2().getPc() == wakeUp2.getPc()){
						instr.getrSrc2().setValue(wakeUp2.getValue());
						instr.setSrc2Dependency(false);
						flag = true;
						//}
					}
			}
			if (wakeUp3 != null) {
				if (instr.getrSrc1().getRegName().equals(wakeUp3.getRegName())) {
					if(instr.getrSrc1().getPc() == wakeUp3.getPc()){
						instr.getrSrc1().setValue(wakeUp3.getValue());
						instr.setSrc1Dependency(false);
						flag = true;
					}
				}
				if (instr.getrSrc2().getRegName() != null)
					if (instr.getrSrc2().getRegName().equals(wakeUp3.getRegName())) {
						//	if(instr.getrSrc2().getPc() == wakeUp2.getPc()){
						instr.getrSrc2().setValue(wakeUp3.getValue());
						instr.setSrc2Dependency(false);
						flag = true;
						//}
					}
			}
			if (wakeUp4 != null) {
				if (instr.getrSrc1().getRegName().equals(wakeUp4.getRegName())) {
					if(instr.getrSrc1().getPc() == wakeUp4.getPc()){
						instr.getrSrc1().setValue(wakeUp4.getValue());
						instr.setSrc1Dependency(false);
						flag = true;
					}
				}
			}
			return flag;
		}

		public void issue() {
			Instruction instr = pipeline.get(Constants.STAGE_DRF2);
			if(instr != null && !branchFlush){
				if(issueQueue.size() < Constants.SIZE_IQ && rob.size() < Constants.SIZE_ROB){
					if(!instr.getType().equals(Constants.TYPE_BR) || (instr.getType().equals(Constants.TYPE_BR) && !branchDispatched)){
						addToIQ(instr);
						addToROB(instr);
						pipeline.put(Constants.STAGE_DRF2, null);
						if(instr.getType().equals(Constants.TYPE_BR)){
							branchDispatched = true;
						}
						if(instr.getOpCode().equals(Constants.INST_HALT)){
							pipeline.put(Constants.STAGE_FETCH, null);
							pipeline.put(Constants.STAGE_DRF1, null);
							pipeline.put(Constants.STAGE_DRF2, null);
							halt = true;
						}
						Constants.getLogger().log(Level.INFO, "Dispatched instruction " + instr.getInstructionInfo());
					}
				}
				else{
					dispatchStall++;
					Constants.getLogger().log(Level.INFO, "Instruction stalled in Dispatch " + instr.getInstructionInfo());
				}
			}
		}

		public void writeBack() {
			int writeCount = 3;
			Object[] rob1 = rob.toArray();
			for(Object obj : rob1){
				ROBEntry entry = (ROBEntry) obj;
				if(entry != null && writeCount > 0){
					if(entry.getInstrType().equals(Constants.TYPE_BR) && entry.isStaus()){
						if(entry.getOther().equals("N")){
							if(isHalt){
								//free all physical registers
								for(Map.Entry<String, PR> reg : RAT.entrySet()){
									Constants.getLogger().log(Level.INFO, "RAT - Physical register entry removed for ", reg.getKey());
									freePRF(reg.getKey(), reg.getValue().getPhyRegName());
								}
							}
							branchDispatched = false;
							branchFlush = false;
							rob.poll();
							pipeline.put(Constants.STAGE_BRFU, null);
						}
						else if(entry.equals(rob.peek())){
							//copy R-RAT to RAT
							if(entry.getResult() != 0){
								if(entry.getrDestArch().equals("X")){
									regFile.PRF.get(entry.getrDestPhyAdd()).setValue(entry.getResult());
									//writePRF(e);
									commit();
									Constants.getLogger().log(Level.INFO, "WB-X register - Write register " + entry.getrDestPhyAdd());
								}
							}
							branchDispatched = false;
							branchFlush = false;
							pipeline.put(Constants.STAGE_BRFU, null);
							//flush Pipeline
							initPipeline();
							Constants.getLogger().log(Level.INFO, "Pipeline flushed ");
							//flush IQ
							issueQueue.clear();
							Constants.getLogger().log(Level.INFO, "IQ flushed ");
							//flush ROB
							rob.clear();
							Constants.getLogger().log(Level.INFO, "ROB flushed ");
							//free all physical registers
							for(Map.Entry<String, PR> reg : RAT.entrySet()){
								Constants.getLogger().log(Level.INFO, "RAT - Physical register entry removed for ", reg.getKey());
								freePRF(reg.getKey(), reg.getValue().getPhyRegName());
							}
							//Copy R-RAT to RAT
							for(Map.Entry<String, PR> reg : R_RAT.entrySet()){
								Constants.getLogger().log(Level.INFO, "R-RAT to RAT - Physical register entry added for ", reg.getKey());

								Object objR[] = freeList.toArray();
								for(Object o : objR){
									String s = o.toString();
									if(s.equals(reg.getValue().getPhyRegName()))
										freeList.remove(o);
								}

								RAT.put(reg.getKey(), reg.getValue());
							}
						}
					}
					else if(entry.isStaus()){
						//Associate WB stages
						switch(entry.getInstrType()){
						case Constants.TYPE_RTR:
							wbINTFU();
							writeCount--;
							break;
						case Constants.TYPE_MEM:
							wbLSFU();
							writeCount--;
							break;
						case Constants.TYPE_MUL:
							if(count == 4){
								wbMULFU();
								writeCount--;
							}
							break;
						}
					}
				}
			}
			commit();
		}

		/*WBMULFU:
		 * Writeback stage for MULFU
		 * */
		private void wbMULFU() {
			Instruction instr = pipeline.get(Constants.STAGE_MULFU);
			pipeline.put(Constants.STAGE_MULFU, null);
			pipeline.put(Constants.STAGE_WBMUL, instr);
			
			if(instr != null){
				for(ROBEntry e : rob){
					if(e.getrDestPhyAdd() != null && e.getrDestPhyAdd().equals(instr.getrDest().getRegName())){
						regFile.PRF.get(instr.getrDest().getRegName()).setValue(e.getResult());
						regFile.PRF.get(instr.getrDest().getRegName()).setValid(true);

						//writePRF(e);
						Constants.getLogger().log(Level.INFO, "WB-MULFU - Write register " + e.getrDestPhyAdd());
						pipeline.put(Constants.STAGE_WBMUL, null);
						break;
					}
				}
			}
		}

		private void commit() {
			ROBEntry entry = rob.peek();
			if(entry != null && entry.isStaus()){
			//Add entry to R-RAT	
			R_RAT.put(entry.getrDestArch(), new PR(entry.getrDestPhyAdd(),true, ""));
			//Update ARF with result from ROB
			regFile.ARF.get(entry.getrDestArch()).setValue(entry.getResult());
			if(!regFile.PRF.get(entry.getrDestPhyAdd()).isAllocated()){
				freePRF(entry.getrDestArch(), entry.getrDestPhyAdd());
			}

			//Remove head of ROB
			rob.poll();
			if(entry.getInstrType().equals(Constants.TYPE_MEM)){
				loadCount++;
			}
			iCount++;
			commit = new Register(entry.getrDestPhyAdd(), entry.getResult(), currCycle, entry.getPcValue());
			Constants.getLogger().log(Level.INFO, "Commit - Write register " + entry.getrDestArch());
			Constants.getLogger().log(Level.INFO, "ROB head removed");
			}
		}

	/*WBLSFU:
	 * Associate WriteBack stage for LSFU
	 * */
	private void wbLSFU() {
			Instruction instr = pipeline.get(Constants.STAGE_LSFU2);
			pipeline.put(Constants.STAGE_LSFU2, null);
			pipeline.put(Constants.STAGE_WBLS, instr);
			
			if(instr != null){
				if(rob.peek().getPcValue() == instr.getPC() && rob.peek().isStaus()){
					for(ROBEntry e : rob){
						if(e.getrDestPhyAdd().equals(instr.getrDest().getRegName())){
							regFile.PRF.get(instr.getrDest().getRegName()).setValue(e.getResult());
							Constants.getLogger().log(Level.INFO, "WB-LSFU - Write register " + e.getrDestPhyAdd());
							pipeline.put(Constants.STAGE_WBLS, null);
							break;
						}
					}
				}
			}
		}

	/*FreePR:
	 * Frees Physical register.
	 * Adds it to freelist in sorted order
	 * */
	private void freePRF(String rDest, String phyReg){
			//Free physical register
			RAT.put(rDest, new PR("", false, ""));
			if(!phyReg.isEmpty()){
				freeList.add(phyReg);				
			}

			//Sorting free list
			String[] stringArray = Arrays.copyOf(freeList.toArray(), freeList.toArray().length, String[].class);
			freeList.clear();
			int[] array = new int[stringArray.length];
			int index = 0;
			for(String str : stringArray){
				array[index++] = Integer.parseInt(str.substring(1));
			}

			Arrays.sort(array);

			for(int i : array){
				freeList.add("P" + i);
			}
		}

		/*WBINTFU:
		 * Assoicate WB stage for INTFU
		 * */
		private void wbINTFU() {
			Instruction instr = pipeline.get(Constants.STAGE_INTFU2);
			pipeline.put(Constants.STAGE_INTFU2, null);
			pipeline.put(Constants.STAGE_WBINT, instr);
			if(instr != null){
				for(ROBEntry e : rob){
					if(e.getrDestPhyAdd() != null && e.getrDestPhyAdd().equals(instr.getrDest().getRegName())){
						regFile.PRF.get(instr.getrDest().getRegName()).setValue(e.getResult());
						regFile.PRF.get(instr.getrDest().getRegName()).setValid(true);

						//writePRF(e);
						Constants.getLogger().log(Level.INFO, "WB-INTFU - Write register " + e.getrDestPhyAdd());
						pipeline.put(Constants.STAGE_WBINT, null);
						break;
					}
				}
			}
		}
	}

	private static Map<String, Instruction> pipeline;

	private int currPC, currCycle;
	private double iCount=0;

	Stages pStage = new Stages();

	private Queue<IQEntry> issueQueue = new LinkedList<IQEntry>();

	private Queue<ROBEntry> rob = new LinkedList<ROBEntry>();

	private Queue<String> freeList = new LinkedList<String>();

	private Map<String, PR> RAT = new LinkedHashMap<String, PR>();

	private Map<String, PR> R_RAT = new LinkedHashMap<String, PR>();

	private List<Instruction> instructionList = new ArrayList<Instruction>();

	private List<String> executedInstructions = new ArrayList<String>();

	private Map<Integer, Instruction> iCache = new LinkedHashMap<Integer, Instruction>();

	private RegisterFile regFile;

	private DataMemory dataMemory = new DataMemory();

	
	public void displayIQ(){
		System.out.println("IQ: ");
		System.out.println("Alloc|FUTYP|lit|1va|1valu|2r|2va|2valu|dest");
		for (IQEntry entry : issueQueue) {
			//System.out.println(entry);
			entry.display();
		}
	}
	
	public void displayROB(){
		System.out.println("ROB: ");
		System.out.println("pc|intyp|rslt|flag|arch|phy|status");
		for (ROBEntry entry : rob) {
			//System.out.println(entry);
			entry.display();
		}
	}

	public void displayURF(){
		System.out.println("\nARF: ");
		for (Map.Entry<String, Register> e : regFile.ARF.entrySet()) {
			if (e.getValue() != null) {
				System.out.print("[" + e.getKey() + "]-> " + e.getValue().getValue() + "  |  ");
			}
		}

		System.out.println("\nPRF: ");
		for (Map.Entry<String, Register> e : regFile.PRF.entrySet()) {
			if (e.getValue() != null) {
				System.out.print("[" + e.getKey() + "]-> " + e.getValue().getValue() + "  |  ");
			}
		}
		System.out.println();
		System.out.println("FreeList: ");
		for (String entry : freeList) {
			System.out.print(entry + "  ");
		}

	}
	
	public void displayTAB(){

		System.out.println("----------RAT----------");
		for(Map.Entry<String, PR> reg : RAT.entrySet()){
			System.out.println(reg.getKey() + "->" + reg.getValue().getPhyRegName());
		}

		System.out.println("----------R-RAT----------");
		for(Map.Entry<String, PR> reg : R_RAT.entrySet()){
			System.out.println(reg.getKey() + "->" + reg.getValue().getPhyRegName());
		}
	}
	
	public void displayMEM(int start, int end){
		int i = 0;
		System.out.println("\nData Memory content: ");
		i = 0;
		for (int j = start; j <= end; j++) {
			System.out.print("[" + j + "] = " + dataMemory.getMemory(j) + "  |  ");
			i++;
			if (i == 10){
				System.out.println();
				i = 0;
			}
		}
		System.out.println(); 
	}
	
	public void displayStats(){
		double result = iCount / (currCycle-1);
		System.out.println("IPC = N/T = " + result);
		System.out.println("Dispatch stall count = " + pStage.dispatchStall);
		System.out.println("IQ stall count = " + pStage.issueStall);
		System.out.println("Committed LOAD = " + pStage.loadCount);
		System.out.println("Committed STORE = " + pStage.storeCount);
	}
	
	/*
	 * This method displays contents of Pipeline, Register File, Data Memory
	 * */
	public void display() {
		int i = 0;

		
		System.out.println("Pipeline");
		for (Map.Entry<String, Instruction> e : pipeline.entrySet()) {
			if (e.getValue() != null)
				System.out.println(e.getKey() + "-> " + e.getValue().getInstructionInfo());
			else
				System.out.println(e.getKey() + "-> IDEAL");
		}

		Constants.getLogger().log(Level.INFO, "\nExecuted Instructions: ");
		for (String instruction : executedInstructions) {
			Constants.getLogger().log(Level.INFO, instruction);
		}
	}

	public int getCurrPC() {
		return currPC;
	}

	public Map<Integer, Instruction> getInstrPCMap() {
		return iCache;
	}

	public List<Instruction> getInstructionList() {
		return instructionList;
	}

	public RegisterFile getRegFile() {
		return regFile;
	}

	/*
	 * This method initializes all the resources
	 * */
	public void initialize() {
		currPC = Constants.PC;
		currCycle = 1;
		initInstructionSet();
		regFile.init();
		initRAT();
		dataMemory.init();
		initPipeline();
		initFreeList();
		//initRenameTables();
	}

	private void initRAT() {
		for (int i = 0; i < Constants.ARCH_REG; i++) {
			RAT.put("R"+i, new PR("", false, ""));
		}
	}

	public void setCurrPC(int currPC) {
		this.currPC = currPC;
	}

	public void setInstrPCMap(Map<Integer, Instruction> instrPCMap) {
		this.iCache = instrPCMap;
	}

	public void setInstructionList(List<Instruction> instructionList) {
		this.instructionList = instructionList;
	}

	public void setRegFile(RegisterFile regFile) {
		this.regFile = regFile;
	}

	/*
	 * This method simulates the pipeline by calling all required methods.
	 * */
	public void simulate(int noCycle) {
		Constants.getLogger().log(Level.INFO, "SIMULATION started");
		try {
			for (int instrCycle = 0; instrCycle < noCycle; instrCycle++) {
				Constants.getLogger().log(Level.INFO, "-------Cycle " + currCycle + " started--------");
				pStage.writeBack();
				//pStage.commit();
				if (pStage.isHalt){
					Constants.getLogger().log(Level.INFO, "HALT instruction in WB in cycle " + currCycle);
					System.out.println("Simulation completed due to HALT in WB");
					break;
				}
				pStage.execute();
				pStage.issue();
				pStage.dispatch();
				pStage.decode();
				pStage.fetch();

				if(pStage.wakeUp1 != null){
					pStage.sendWakeup(pStage.wakeUp1);
					pStage.wakeUp1 = null;
				}
				if(pStage.wakeUp2 != null){
					pStage.sendWakeup(pStage.wakeUp2);
					pStage.wakeUp2 = null;
				}
				if(pStage.wakeUp3 != null){
					pStage.sendWakeup(pStage.wakeUp3);
					pStage.wakeUp3 = null;
				}
				Constants.getLogger().log(Level.INFO, "-------Cycle " + currCycle + " finished--------");
				currCycle++;
			}
		}catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception:" +  e.getMessage());
			System.out.println("Current cycle: "+ currCycle + "CurrPC: "+currPC+" Current instruction: " + iCache.get(currPC).getInstructionInfo()); 
		}
	}

	public void initFreeList(){
		for (Map.Entry<String, Register> e : regFile.PRF.entrySet()){
			freeList.add(e.getValue().getRegName());
		}
	}

	private void initInstructionSet() {
		int temp = Constants.PC;

		for (Instruction instruction : instructionList) {
			instruction.setPC(temp);
			temp += Constants.SIZE;
		}

		Constants.getLogger().log(Level.INFO, "Instruction list initialized with reset to PC.");

		for (Instruction instruction : instructionList) {
			iCache.put(instruction.getPC(), new Instruction(instruction));
		}

		Constants.getLogger().log(Level.INFO,"I-cache initialized.");
	}

	@SuppressWarnings("serial")
	private void initPipeline() {
		pipeline = new LinkedHashMap<String, Instruction>() {
			{
				put(Constants.STAGE_FETCH, null);
				put(Constants.STAGE_DRF1, null);
				put(Constants.STAGE_DRF2,null);
				put(Constants.STAGE_INTFU1, null);
				put(Constants.STAGE_INTFU2, null);
				put(Constants.STAGE_MULFU, null);
				put(Constants.STAGE_LSFU1, null);
				put(Constants.STAGE_LSFU2, null);
				put(Constants.STAGE_BRFU, null);
				put(Constants.STAGE_WBINT, null);
				put(Constants.STAGE_WBLS, null);
				put(Constants.STAGE_WBMUL, null);
			}
		};
		Constants.getLogger().log(Level.INFO,"Pipeline initialized.");
	}
}
