package colony;

import java.util.HashMap;

public interface ColonyVocabulary {

	// Requests and Replies
	public static final int GIVE_MAP = 101;
	public static final int EXPLORE_LOCATION = 102;
	public static final int MINE_RESOURCES = 103;
	public static final int UPDATE_MAP = 104;
	public static final int GIVE_TASK = 105;
	public static final int DEPOSIT_RESOURCES = 106;
	public static final int FAILURE = 404;
	public static final int SUCCESS = 777;

	// Tasks
	public static final int STARTUP = 900;
	public static final int EXPLORE = 901;
	public static final int MINE = 902;
	public static final int IDLE = 903;
	public static final int TRADE = 904;

	// Directions
	public static final int UP = 10;
	public static final int DOWN = 20;
	public static final int LEFT = 30;
	public static final int RIGHT = 40;

	// Map chars
	public static final char UNEXPLORED = '?';
	public static final char EMPTY = '.';
	public static final char RESOURCES = '$';
	public static final char CLOSED_MINE = 'X';
	public static final char COLONY_1 = '1';
	public static final char COLONY_2 = '2';
	public static final char COLONY_3 = '3';
	public static final char COLONY_4 = '4';
	public static final char COLONY_5 = '5';
	public static final char COLONY_6 = '6';
	public static final char COLONY_7 = '7';
	public static final char COLONY_8 = '8';
	public static final char COLONY_9 = '9';

	// Service_types
	public static final String WORKER = "worker";
	public static final String BOOKER = "booker";
	public static final String LEADER = "leader";
	public static final String ENVIRONMENT = "env";

}
