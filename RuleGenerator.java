package tracks.ruleGeneration.brkdncr94;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import core.game.GameDescription.SpriteData;
import core.game.SLDescription;
import core.generator.AbstractRuleGenerator;
import tools.ElapsedCpuTimer;
import tools.LevelAnalyzer;

/**
 * This is a constructive rule generator based on the sample provided by AhmedKhalifa
 * Several additions and changes have been made, which inclue:
 * -Ability to destroy resources, collectibles, walls etc with some probability (These changes should result in more differentiated games)
 * -Refined termination conditions
 */

public class RuleGenerator extends AbstractRuleGenerator{
	/**
	 * a Level Analyzer object used to analyze the game sprites
	 */
	private LevelAnalyzer la;

	/**
	 *  array of different interactions that movable objects (contains also NPCs) can do when hitting the walls
	 */
	private String[] movableWallInteraction = new String[]{"stepBack", "flipDirection", "reverseDirection",
			"turnAround", "wrapAround"};

	/**
	 * percentages used to decide
	 */
	private double doorCollectibleProb = 0.5;
	private double killIfHasLessProb = 0.2;
	private double killResourceProb = 0.5;
	private double killResourceScoreProb = 0.3;
	private double destroyWallProb = 0.4;
	private double wallPercentageProb = 0.5;
	private double spikeProb = 0.5;
	private double doubleNPCsProb = 0.5;
	private double harmfulMovableProb = 0.3;
	private double usefulMovableProb = 0.7;
	private double firewallProb = 0.05;
	private double scoreSpikeProb = 0.1;
	private double randomNPCProb = 0.5;
	private double spawnedProb = 0.5;
	private double bomberProb = 0.5;

	/**
	 * a list of suggested interactions for the generated game
	 */
	private ArrayList<String> interactions;
	/**
	 * a list of suggested termination conditions for the generated game
	 */
	private ArrayList<String> terminations;

	/**
	 * the sprite that the generator think is a wall sprite
	 */
	private SpriteData wall;
	/**
	 * array of all door sprites
	 */
	private ArrayList<SpriteData> exit;
	/**
	 * array of all collectible sprites
	 */
	private ArrayList<String> collectible;
	/**
	 * a certain unmovable object that is used as a collectible object
	 */
	private SpriteData score;
	/**
	 * a certain unmovable object that is used as a spike object
	 */
	private SpriteData spike;

	/**
	 * random object used in generating different games
	 */
	private Random random;

	/**
	 * Array of all different types of harmful objects (can kill the player)
	 */
	private ArrayList<String> harmfulObjects;
	
	/**
	 * Array of harmful NPCs (can attack and kill the player)
	 */
	private ArrayList<String> harmfulNPCs;
	
	/**
	 * Array of all different types of fleeing NPCs
	 */
	private ArrayList<String> fleeingNPCs;
	
	
	// SpriteData arrays to be used to form interactions
	private SpriteData[] avatar;
	private SpriteData[] resources;
	private SpriteData[] movables;
	private SpriteData[] NPCs;
	private SpriteData[] spawners;
	private SpriteData[] immovables;
	private SpriteData[] portals;
	
	private String criticalEnemyNPC;
	private String criticalCollectible;
	private String npcToCatch;
	

	/**
	 * Constructor that initialize the constructive algorithm
	 * @param sl	SLDescription object contains information about the
	 * 			current game and level
	 * @param time	the amount of time allowed for initialization
	 */
	public RuleGenerator(SLDescription sl, ElapsedCpuTimer time){
		//Initialize everything
		la = new LevelAnalyzer(sl);
		avatar = la.getAvatars(false);
		resources = la.getResources(true);
		movables = la.getMovables(false);
		NPCs = la.getNPCs(false);
		spawners = la.getSpawners(false);
		immovables = la.getImmovables(1, (int)(scoreSpikeProb * la.getArea()));
		portals = la.getPortals(true);
		
		
		
		interactions = new ArrayList<String>();
		terminations = new ArrayList<String>();

		random = new Random();
		harmfulObjects = new ArrayList<String>();
		harmfulNPCs = new ArrayList<String>();
		fleeingNPCs = new ArrayList<String>();
		collectible = new ArrayList<String>();

		//Identify the wall object
		wall = null;
		SpriteData[] temp = la.getBorderObjects((1.0 * la.getPerimeter()) / la.getArea(), this.wallPercentageProb);
		if(temp.length > 0){
			wall = temp[0];
			for(int i=0; i<temp.length; i++){
				if(la.getNumberOfObjects(temp[i].name) < la.getNumberOfObjects(wall.name)){
					wall = temp[i];
				}
			}
		}

		//identify the exit sprite
		exit = new ArrayList<SpriteData>();
		for(int i=0; i<portals.length; i++){
			if(!portals[i].type.equalsIgnoreCase("portal")){
				exit.add(portals[i]);
			}
		}

		//identify the score and spike sprites
		ArrayList<SpriteData> tempList = new ArrayList<SpriteData>();
		score = null;
		spike = null;
		temp = la.getImmovables(1, (int)(scoreSpikeProb * la.getArea()));
		if (immovables.length > 0) {
			if (wall == null) {
				score = immovables[random.nextInt(immovables.length)];
				spike = immovables[random.nextInt(immovables.length)];
			}
			else {
				tempList = new ArrayList<SpriteData>();
				SpriteData[] relatedSprites = la.getSpritesOnSameTile(wall.name);
				for (int i = 0; i < temp.length; i++) {
					for (int j = 0; j < relatedSprites.length; j++) {
						if (!temp[i].name.equals(relatedSprites[j].name)) {
							tempList.add(temp[i]);
						}
					}
					if(relatedSprites.length == 0){
						tempList.add(temp[i]);
					}
				}

				score = tempList.get(random.nextInt(tempList.size()));
				spike = tempList.get(random.nextInt(tempList.size()));
			}
		}
	}

	/**
	 * Check if this spritename is the avatar
	 * @param spriteName	the input sprite name
	 * @return			true if its the avatar or false otherwise
	 */
	private boolean isAvatar(String spriteName){
		SpriteData[] avatar = la.getAvatars(false);
		for(int i=0; i<avatar.length; i++){
			if(avatar[i].equals(spriteName)){
				return true;
			}
		}
		return false;
	}

	/**
	 * get the interactions of everything with wall sprites
	 */
	private void getWallInteractions(){
		//Do walls act like fire (harmful for the avatar)?
		boolean avatarFireWall = this.random.nextDouble() < firewallProb &&
				wall != null;
		
		String criticalResource = "";
		if(resources != null && resources.length > 0) {
			criticalResource = resources[random.nextInt(resources.length)].name;
			//System.out.println("Num of resources: " + resources.length);
		}
		else {
			//System.out.println("Length of resources is 0.");

		}

		//Avatar interaction with wall
		String action = "stepBack";
		if(avatarFireWall){
			if(random.nextDouble() > killIfHasLessProb) {
				action = "killSprite";
			}
			else if(!criticalResource.equals("")) {
				int limit = (int) (la.getNumberOfObjects(criticalResource)/2) + 1;
				action = "killIfHasLess resource=" + criticalResource + " limit=" + limit;
			}
		}
		
		for (int i = 0; i < avatar.length; i++) {
			
			interactions.add(avatar[i].name + " EOS > stepBack");
			
			if(wall != null) {
				interactions.add(avatar[i].name + " " + wall.name + " > " + action);
				
				if(!avatarFireWall) { // with a probability, avatar can destroy walls with bullets
					if(random.nextDouble() < destroyWallProb) { 
						for (int k = 0; k < avatar[i].sprites.size(); k++) {						
							interactions.add(avatar[i].sprites.get(k) + " " + wall.name + " > killSprite");
							interactions.add(wall.name + " " + avatar[i].sprites.get(k) + " > killSprite");						
						}
					}				
				}
			}						
		}
		
			

		//Get the interaction between all movable objects (including npcs) with wall or EOS
		boolean npcFireWall = this.random.nextDouble() < firewallProb &&
				wall != null && fleeingNPCs.size() == 0;
		
		action = movableWallInteraction[random.nextInt(movableWallInteraction.length)];		
		if(npcFireWall){
			action = "killSprite";
		}
		for (int i = 0; i < movables.length; i++) {
			
			interactions.add(movables[i].name + " EOS > " + action);
			
			if(wall != null) {
				if(random.nextDouble() < destroyWallProb) {
					interactions.add(wall.name + " " + movables[i].name + " > killSprite");
					interactions.add(movables[i].name + " " + wall.name + " > killSprite");
				}
				else{
					interactions.add(movables[i].name + " " + wall.name + " > " + action);
				}
			}			
		}
		
		action = movableWallInteraction[random.nextInt(movableWallInteraction.length)];
		if(npcFireWall){
			action = "killSprite";
		}		
		for (int i = 0; i < NPCs.length; i++) {
			
			interactions.add(NPCs[i].name + " EOS > " + action);
			
			if(wall != null) {
				interactions.add(NPCs[i].name + " " + wall.name + " > " + action);
			}
		}
		
		
		
	}

	/**
	 * get the interactions of all sprites with resource sprites
	 */
	private void getResourceInteractions(){

		//make the avatar collect the resources
		for(int i=0; i<avatar.length; i++){
			for(int j=0; j<resources.length; j++){
				interactions.add(resources[j].name + " " + avatar[i].name + " > collectResource");				
			}
		}
	}

	/**
	 * get the interactions of all sprites with spawner sprites
	 */
	private void getSpawnerInteractions(){

		//make the spawned object harmful to the avatar with a chance to be useful
		if(random.nextDouble() < spawnedProb){
			for (int i = 0; i < avatar.length; i++) {
				for (int j = 0; j < spawners.length; j++) {
					for (int k = 0; k < spawners[j].sprites.size(); k++) {
						harmfulObjects.add(spawners[j].sprites.get(k));
						interactions.add(avatar[i].name + " " + spawners[j].sprites.get(k) + " > killSprite");					
					}
				}
			}
		}
		else{
			for (int i = 0; i < avatar.length; i++) {
				for (int j = 0; j < spawners.length; j++) {
					for (int k = 0; k < spawners[j].sprites.size(); k++) {
					    if(!harmfulObjects.contains(spawners[j].sprites.get(k))){
						collectible.add(spawners[j].sprites.get(k));
						interactions.add(spawners[j].sprites.get(k) + " " + avatar[i].name + " > killSprite scoreChange=1");
					    }
					}
				}
			}
		}
		
		if(random.nextDouble() < killResourceProb){ // make the spawned objects destroy resources
			for (int i = 0; i < resources.length; i++) {
				for (int j = 0; j < spawners.length; j++) {
					for (int k = 0; k < spawners[j].sprites.size(); k++) {
						interactions.add(resources[i].name + " " + spawners[j].sprites.get(k) + " > killSprite");					
					}
				}
			}
		}
		
		for (int j = 0; j < spawners.length; j++) {
		    for (int k = 0; k < spawners[j].sprites.size(); k++) {
		    	if(harmfulObjects.contains(spawners[j].sprites.get(k))){
		    		harmfulObjects.add(spawners[j].name);
		    		break;
		    	}
		    }
		}
		
		for (int j = 0; j < spawners.length; j++) {
		    for (int k = 0; k < spawners[j].sprites.size(); k++) {
		    	if(collectible.contains(spawners[j].sprites.get(k))){
		    		collectible.add(spawners[j].name);
		    		break;
		    	}
		    }
		}
	}

	/**
	 * get the interactions of all sprites with immovable sprites
	 */
	private void getImmovableInteractions(){

		//If we have a score object make the avatar can collect it
		if(score != null){
			for(int i=0; i<avatar.length; i++){
				collectible.add(score.name);
				interactions.add(score.name + " " + avatar[i].name + " > killSprite scoreChange=1");
			}
		}

		//If we have a spike object make it kill the avatar with a change to be a super collectible sprite
		if (spike != null && !spike.name.equalsIgnoreCase(score.name)) {
			if (random.nextDouble() < spikeProb) {
				harmfulObjects.add(spike.name);
				for (int i = 0; i < avatar.length; i++) {
					interactions.add(avatar[i].name + " " + spike.name + " > killSprite");
				}
			}
			else {
				for (int i = 0; i < avatar.length; i++) {
					collectible.add(spike.name);
					interactions.add(spike.name + " " + avatar[i].name + " > killSprite scoreChange=2");
				}
			}
		}
	}

	/**
	 * get the interactions of all sprites with avatar sprites
	 */
	private void getAvatarInteractions(){

		//Kill the avatar bullet, kill any harmful objects
		
		if(harmfulNPCs.size() > 0) {
			boolean foundCritical = false;
			int counter = 100; // loop should terminate after a while if we can't find a suitable NPC
			int rnd;
			SpriteData[] inMapSpawners = la.getSpawners(true);
			
			while(!foundCritical) {
				
				rnd = random.nextInt(harmfulNPCs.size()); // designate one random NPC as critical to use as a win condition
				criticalEnemyNPC = harmfulNPCs.get(rnd);
				
				if(la.getNumberOfObjects(criticalEnemyNPC) > 0) {
					foundCritical = true;
				}
				else {
					for(int i=0; i<inMapSpawners.length; i++) {
						for(int j=0; j<inMapSpawners[i].sprites.size(); j++) {
							if(inMapSpawners[i].sprites.get(j).equals(criticalEnemyNPC)) {
								foundCritical = true;
							}
						}
					}
				}
				counter = counter - 1;
				if(counter == 0) {
					foundCritical = true;
					criticalEnemyNPC = null;
				}				
			}			
		}
		
		if(fleeingNPCs.size() > 0) { // identify a fleeing NPC to catch for a winning condition
			boolean foundFleeing = false;
			int counter = 100; // loop should terminate after a while if we can't find a suitable NPC
			int rnd;
			SpriteData[] inMapSpawners = la.getSpawners(true);
			
			while(!foundFleeing) {
				
				rnd = random.nextInt(fleeingNPCs.size()); // designate one random NPC as critical to use as a win condition
				npcToCatch = fleeingNPCs.get(rnd);
				
				if(la.getNumberOfObjects(npcToCatch) > 0) {
					foundFleeing = true;
				}
				else {
					for(int i=0; i<inMapSpawners.length; i++) {
						for(int j=0; j<inMapSpawners[i].sprites.size(); j++) {
							if(inMapSpawners[i].sprites.get(j).equals(npcToCatch)) {
								foundFleeing = true;
							}
						}
					}
				}
				counter = counter - 1;
				if(counter == 0) {
					foundFleeing = true;
					npcToCatch = null;
				}				
			}			
		}
		
		if(collectible.size() > 0){
			int rnd = 0;
			int counter = 100;
			boolean collectibleFound = false;
			
			while(!collectibleFound) {
				rnd = random.nextInt(collectible.size()); // choose one random critical collectible to use as a win condition
				if(la.getNumberOfObjects(collectible.get(rnd)) > 0) {
					criticalCollectible = collectible.get(rnd);
					collectibleFound = true;
				}
				counter = counter - 1;
				if(counter == 0) {
					collectibleFound = true;
					criticalCollectible = null;
				}
			}
		}
		
		for(int i=0; i<avatar.length; i++){
			for (int j = 0; j < avatar[i].sprites.size(); j++) { // kill harmful objects
				for (int k = 0; k < harmfulObjects.size(); k++) {
					interactions.add(harmfulObjects.get(k) + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=1");
					interactions.add(avatar[i].sprites.get(j) + " " + harmfulObjects.get(k) + " > killSprite");
				}
				
				for (int k = 0; k < harmfulNPCs.size(); k++) {
					if(harmfulNPCs.get(k).equals(criticalEnemyNPC)) {
						interactions.add(harmfulNPCs.get(k) + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=2");
					}
					else {
						interactions.add(harmfulNPCs.get(k) + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=1");
					}					
					interactions.add(avatar[i].sprites.get(j) + " " + harmfulNPCs.get(k) + " > killSprite");
				}
				
				if(random.nextDouble() < killResourceProb){ // with a probability, can kill resource objects
					for (int k = 0; k < resources.length; k++) {
						interactions.add(avatar[i].sprites.get(j) + " " + resources[k].name + " > killSprite");
						
						if(random.nextDouble() < killResourceScoreProb) { // with some probability, destroying resource increases score
							interactions.add(resources[k].name + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=1");
						}
						else { // else, destroying the resource doesn't do the player any good
							interactions.add(resources[k].name + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=-1");
						}	
					}
				}
				
				// with a probability can kill collectibles
				if(random.nextDouble() < 0){
				//if(random.nextDouble() < killResourceProb){
					for (int k = 0; k < collectible.size(); k++) {
						interactions.add(avatar[i].sprites.get(j) + " " + collectible.get(k) + " > killSprite");
						
						if(random.nextDouble() < killResourceScoreProb) { // with some probability, destroying the collectible increases score
							interactions.add(collectible.get(k) + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=1");
						}
						else { // else, destroying the collectible doesn't do the player any good
							interactions.add(collectible.get(k) + " " + avatar[i].sprites.get(j) + " > killSprite scoreChange=-1");
						}	
					}
				}
				
				
			}			
		}
	}

	/**
	 * get the interactions of all sprites with portal sprites
	 */
	private void getPortalInteractions() {

		SpriteData door = null;
		//make the exits die with collision of the player (going through them)
		for (int i = 0; i < avatar.length; i++) {
			for (int j = 0; j < exit.size(); j++) {
				interactions.add(exit.get(j).name + " " + avatar[i].name + " > killSprite");
			}
			
			
			
			
		}
		//If they are Portal type then u can teleport toward it
		for (int i = 0; i < portals.length; i++) {
			for (int j = 0; j < avatar.length; j++) {
				if (portals[i].type.equalsIgnoreCase("Portal")) {
					interactions.add(avatar[j].name + " " + portals[i].name + " > teleportToExit");
				}
			}
		}
	}

	/**
	 * get the interactions of all sprites with npc sprites
	 */
	private void getNPCInteractions(){

		for(int i=0; i<NPCs.length; i++){
			//If its fleeing object make it useful
			if (NPCs[i].type.equalsIgnoreCase("fleeing")) {
				for(int j=0; j<NPCs[i].sprites.size(); j++){
					fleeingNPCs.add(NPCs[i].sprites.get(j));
					interactions.add(NPCs[i].name + " " + NPCs[i].sprites.get(j) + " > killSprite scoreChange=1");
				}
			}
			else if (NPCs[i].type.equalsIgnoreCase("bomber") || NPCs[i].type.equalsIgnoreCase("randombomber")
					|| NPCs[i].type.equalsIgnoreCase("bomberrandommissile") || NPCs[i].type.equalsIgnoreCase("spreader")) {
				//make the bomber harmful for the player
				for(int j=0; j<avatar.length; j++){
					harmfulNPCs.add(NPCs[i].name);
					interactions.add(avatar[j].name + " " + NPCs[i].name + " > killSprite");
				}
				//make the spawned object harmful
				if(this.random.nextDouble() < bomberProb){
					for (int j = 0; j < NPCs[i].sprites.size(); j++) {
						harmfulObjects.add(NPCs[i].sprites.get(j));
						interactions.add(avatar[j].name + " " + NPCs[i].sprites.get(j) + " > killSprite");
					}
				}
				//make the spawned object useful
				else{
					for (int j = 0; j < NPCs[i].sprites.size(); j++) {
						interactions.add(NPCs[i].sprites.get(j) + " " + avatar[j].name + " > killSprite scoreChange=1");
					}
				}
			}
			else if (NPCs[i].type.equalsIgnoreCase("chaser") || NPCs[i].type.equalsIgnoreCase("AlternateChaser")
					|| NPCs[i].type.equalsIgnoreCase("RandomAltChaser")) {
				//make chasers harmful for the avatar
				for(int j=0; j<NPCs[i].sprites.size(); j++){
					if(isAvatar(NPCs[i].sprites.get(j))){
						for(int k=0; k<avatar.length; k++){
							harmfulNPCs.add(NPCs[i].name);
							interactions.add(avatar[k].name + " " + NPCs[i].name + " > killSprite");
						}
					}
					else{
						if(random.nextDouble() < doubleNPCsProb){
							interactions.add(NPCs[i].sprites.get(j) + " " + NPCs[i].name + " > killSprite");
						}
						else{
							interactions.add(NPCs[i].sprites.get(j) + " " + NPCs[i].name + " > transformTo stype=" + NPCs[i].name);
						}

					}
				}
			}
			else if (NPCs[i].type.equalsIgnoreCase("randomnpc")) {
				//random npc are harmful to the avatar
				if(this.random.nextDouble() < randomNPCProb){
					for (int j = 0; j < avatar.length; j++) {
						harmfulNPCs.add(NPCs[i].name);
						interactions.add(avatar[j].name + " " + NPCs[i].name + " > killSprite");
					}
				}
				//random npc are userful to the avatar
				else{
					for (int j = 0; j < avatar.length; j++) {
						collectible.add(NPCs[i].name);
						interactions.add(NPCs[i].name + " " + avatar[j].name + " > killSprite scoreChange=1");
					}
				}
			}
		}
	}

	/**
	 * get the interactions of all sprites with movable sprites
	 */
	private void getMovableInteractions(){

		for(int j=0; j<movables.length; j++){
			//Check if the movable object is not avatar or spawned child
			boolean found = false;
			for(int i=0; i<avatar.length; i++){
				if(avatar[i].sprites.contains(movables[j].name)){
					found = true;
				}
			}
			for(int i=0; i<spawners.length; i++){
				if(spawners[i].sprites.contains(movables[j].name)){
					found = true;
				}
			}
			if(!found){
				//Either make them harmful or useful
				double rnd = random.nextDouble();
				if(rnd < harmfulMovableProb){
					for(int i=0; i<avatar.length; i++){
						harmfulObjects.add(movables[j].name);
						interactions.add(avatar[i].name + " " + movables[j].name + " > killSprite");
					}
				}
				else if(rnd > usefulMovableProb){
					for(int i=0; i<avatar.length; i++){
						collectible.add(movables[j].name);
						interactions.add(movables[j].name + " " + avatar[i].name + " > killSprite scoreChange=1");
					}
				}
				else {
					for(int i=0; i<avatar.length; i++){
						interactions.add(avatar[i].name + " " + movables[j].name + " > pullWithIt");
					}
				}
			}
		}
	}

	/**
	 * get the termination condition for the generated game
	 */
	private void getTerminations(){
		
		//If you have a door object make it the winning condition
		if(exit.size() > 0){
			SpriteData door = null;
			for(int i=0; i<exit.size(); i++){
				if(exit.get(i).type.equalsIgnoreCase("door")){
					door = exit.get(i);
					break;
				}
			}

			if(door != null){
				
				if(collectible.size() > 0 && criticalCollectible != null && random.nextDouble() < doorCollectibleProb){
					terminations.add("MultiSpriteCounter stype1=" + criticalCollectible + " stype2=" + door.name + " limit=0 win=True");
					terminations.add("Timeout limit=" + (2000 + random.nextInt(6) * 100) + " win=False"); // put a timer on the game to make sure it ends
					//System.out.println("MultiSpriteCounter stype1=(collectible) stype2=(door) limit=0 win=True");
				}
				else {
					terminations.add("SpriteCounter stype=" + door.name + " limit=0 win=True");
					//System.out.println("SpriteCounter stype=(door) limit=0 win=True");
				}
			}
			else if(collectible.size() > 0 && criticalCollectible != null){
				terminations.add("SpriteCounter stype=" + criticalCollectible + " limit=0 win=True");
				//System.out.println("SpriteCounter stype=(collectible) limit=0 win=True");
				terminations.add("Timeout limit=" + (2000 + random.nextInt(6) * 100) + " win=False"); // put a timer on the game to make sure it ends
			}
		} //otherwise pick any other exit object
		/*else if(collectible.size() > 0 && criticalCollectible != null){
			terminations.add("SpriteCounter stype=" + criticalCollectible + " limit=0 win=True");
			System.out.println("SpriteCounter stype=(collectible) limit=0 win=True");
			terminations.add("Timeout limit=" + (2000 + random.nextInt(6) * 100) + " win=False"); // put a timer on the game to make sure it ends
		}*/
		else {
			//If we have fleeing NPCs use them as winning condition
			if (fleeingNPCs.size() > 0 && npcToCatch != null) {
				terminations.add("SpriteCounter stype=" + npcToCatch + " limit=0 win=True");
				//System.out.println("SpriteCounter stype=(fleeing) limit=0 win=True");
				terminations.add("Timeout limit=" + (1000 + random.nextInt(6) * 100) + " win=False"); // put a timer on the game to make sure it ends
			}
			else if(harmfulNPCs.size() > 0 && this.la.getAvatars(true)[0].sprites.size() > 0 && criticalEnemyNPC != null){
				terminations.add("SpriteCounter stype=" + criticalEnemyNPC + " limit=0 win=True");
				//System.out.println("SpriteCounter stype=(harmful) limit=0 win=True");
				terminations.add("Timeout limit=" + (1000 + random.nextInt(6) * 100) + " win=False"); // put a timer on the game to make sure it ends
			}
			//Otherwise use timeout as winning condition
			else {
				terminations.add("Timeout limit=" + (500 + random.nextInt(7) * 100) + " win=True");
				//System.out.println("Timeout limit=" + (500 + random.nextInt(7) * 100) + " win=True");
			}			
		}

		//Add the losing condition which is the player dies
		if(harmfulObjects.size() > 0 || harmfulNPCs.size() > 0){
			SpriteData[] usefulAvatar = this.la.getAvatars(true);
			for(int i=0; i<usefulAvatar.length; i++){
				terminations.add("SpriteCounter stype=" + usefulAvatar[i].name + " limit=0 win=False");
			}
		}
	}

    
    /**
     * get the generated interaction rules and termination rules
     * @param sl	SLDescription object contain information about the game
     * 			sprites and the current level
     * @param time	the amount of time allowed for the rule generator
     * @return		two arrays the first contains the interaction rules
     * 			while the second contains the termination rules
     */
    @Override
    public String[][] generateRules(SLDescription sl, ElapsedCpuTimer time) {
	this.interactions.clear();
	this.terminations.clear();
	this.collectible.clear();
	this.harmfulObjects.clear();
	this.fleeingNPCs.clear();
	
	this.getResourceInteractions();
	this.getImmovableInteractions();
	this.getNPCInteractions();
	this.getSpawnerInteractions();
	this.getPortalInteractions();
	this.getMovableInteractions();
	this.getWallInteractions();
	this.getAvatarInteractions();
	
	this.getTerminations();
	
	return new String[][]{interactions.toArray(new String[interactions.size()]), terminations.toArray(new String[terminations.size()])};
    }
    
    @Override
    public HashMap<String, ArrayList<String>> getSpriteSetStructure() {
        HashMap<String, ArrayList<String>> struct = new HashMap<String, ArrayList<String>>();
        HashMap<String, Boolean> testing = new HashMap<String, Boolean>();
        
        if(fleeingNPCs.size() > 0){
            struct.put("fleeing", new ArrayList<String>());
        }
        for(int i=0; i<this.fleeingNPCs.size(); i++){
            if(!testing.containsKey(this.fleeingNPCs.get(i))){
        	testing.put(this.fleeingNPCs.get(i), true);
        	struct.get("fleeing").add(this.fleeingNPCs.get(i));
            }
        }
        
        if(harmfulObjects.size() > 0){
            struct.put("harmful", new ArrayList<String>());
        }
        for(int i=0; i<this.harmfulObjects.size(); i++){
            if(!testing.containsKey(this.harmfulObjects.get(i))){
        	testing.put(this.harmfulObjects.get(i), true);
        	struct.get("harmful").add(this.harmfulObjects.get(i));
            }
        }
        if(collectible.size() > 0){
            struct.put("collectible", new ArrayList<String>());
        }
        for(int i=0; i<this.collectible.size(); i++){
            if(!testing.containsKey(this.collectible.get(i))){
        	testing.put(this.collectible.get(i), true);
        	struct.get("collectible").add(this.collectible.get(i));
            }
        }
        
        return struct;
    }

}
