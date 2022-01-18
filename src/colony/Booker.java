package colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import colony.Leader.AuctionMiningTask;
import colony.Leader.HandleRequest;
import colony.Leader.ListenForMessages;
import colony.Leader.RequestWorkerMap;
import colony.Leader.RequestWorkerTask;
import colony.Leader.StartMainLoop;
import colony.behaviours.DelayBehaviour;
import colony.behaviours.PrintContacts;
import colony.behaviours.RegisterInDF;
import colony.behaviours.myReceiver;
import colony.environment.PersonalMap;
import colony.environment.PersonalMapLightWeight;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Booker extends Agent implements ColonyVocabulary {

	// Utility variables
	Random rnd = new Random(hashCode());
	Random uniqueRNG = new Random(System.currentTimeMillis());
	MessageTemplate requestTemplate = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
	Behaviour flusher;
	SequentialBehaviour startBooker;

	// Colony information
	int ColonyBANK;
	DFAgentDescription[] result;
	List<DFAgentDescription> myWorkers = new ArrayList<DFAgentDescription>();
	DFAgentDescription myLeader;
	DFAgentDescription myEnvironment;
	String myColony;
	String otherColony;
	String myServiceType;
	int mapScore;
	int resourceScore;
	int totalScore;

	// World information
	HashMap<Integer, String> SpeechMap = new HashMap<Integer, String>();
	PersonalMapLightWeight map4msg;
	PersonalMap myMap = null;

	protected void setup() {

		populateSpeechMap();

		System.out.println(getLocalName());
		String[] utiName = getLocalName().split("#");

		switch (utiName[1].charAt(0)) {
		case COLONY_1:
			myColony = "'K1'";
			otherColony = "'K2'";
			break;
		case COLONY_2:
			myColony = "'K2'";
			otherColony = "'K1'";
		default:
			break;
		}

		myServiceType = BOOKER;

		// -------- BEHAVIOURS --------

		// start msg cleanup
		if (flusher == null) {
			flusher = new GCAgent(this, 10 * 1000);
			addBehaviour(flusher);
		}

		// register Agent to DF
		addBehaviour(new RegisterInDF(this, myServiceType, myColony));

		// Look for all colony agents after delay, then request map
		SequentialBehaviour findAgents = new SequentialBehaviour();
		// findAgents.addSubBehaviour(new DelayBehaviour(this, 500));
		findAgents.addSubBehaviour(new GetContacts(this, WORKER, myColony));
		findAgents.addSubBehaviour(new GetContacts(this, LEADER, myColony));
		findAgents.addSubBehaviour(new GetContacts(this, ENVIRONMENT, null));

		startBooker = new SequentialBehaviour();
		startBooker.addSubBehaviour(findAgents);
		startBooker.addSubBehaviour(new RequestMap());
		startBooker.addSubBehaviour(new DelayBehaviour(this, 5000));

		startBooker.addSubBehaviour(new StartMainLoop());
		// Listens for Request Messages
		addBehaviour(new ListenForMessages());
		addBehaviour(startBooker);
		// addBehaviour(new SelfDestruct(this, 5 * 1000));
	}

	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (Exception e) {
			// TODO: handle exception
		}
		// System.exit(0);
	}

	class StartMainLoop extends OneShotBehaviour {

		@Override
		public void action() {

			// Collect maps from workers every 8 seconds
			TickerBehaviour updateMap = new TickerBehaviour(myAgent, 8 * 1000) {

				protected void onTick() {

					if (myLeader == null)
						addBehaviour(new GetContacts(myAgent, LEADER, myColony));
					else {
						addBehaviour(new RequestLeaderMap(myAgent));
						addBehaviour(new CalculateResult());
					}

				}
			};

			// All LOOP actions
			ParallelBehaviour allActions = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
			allActions.addSubBehaviour(updateMap);

			addBehaviour(allActions);
		}

	}

	class ListenForMessages extends CyclicBehaviour {
		@Override
		public void action() {
			ACLMessage msg = receive(requestTemplate);
			if (msg != null)
				addBehaviour(new HandleRequest(myAgent, msg));
			block();
		}
	}

	// Gets map from environment
	class RequestMap extends OneShotBehaviour {
		public void action() {
			String request = GIVE_MAP + "#" + COLONY_1;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myEnvironment.getName());
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addBehaviour(new myReceiver(myAgent, 3000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- INFORM from "
								+ msg.getSender().getLocalName() + ".  " + GIVE_MAP + "map req. success");

						try {
							PersonalMapLightWeight freshMap = (PersonalMapLightWeight) msg.getContentObject();
							myMap = new PersonalMap(freshMap.getMap());
						} catch (Exception e) {
							// TODO: handle exception
						}

						switch (myColony.charAt(2)) {
						case COLONY_1:
							myMap.updateMap(0, myMap.getMapYdim() / 2, COLONY_1);
							break;
						case COLONY_2:
							myMap.updateMap(myMap.getMapXdim() - 1, myMap.getMapYdim() / 2, COLONY_2);
							break;
						default:
							System.out.print("FAILED TO CHOOSE COLONY");
							break;
						}
						System.out.print(myAgent.getLocalName() + " <- Map gen success:");
						myMap.printMap();

					} else {
						System.out.println(myAgent.getLocalName() + " <- Map gen fail");
					}
				}
			});
		}
	}

	class CalculateResult extends OneShotBehaviour {

		@Override
		public void action() {
			mapScore = myMap.countInstance('$') * 5 + myMap.countInstance('X') * 5 - myMap.countInstance('?');
			resourceScore = ColonyBANK * 10;
			totalScore = mapScore + resourceScore;

			System.out.println("\n-----------------------------");
			System.out.println("SCORE: " + totalScore);
			System.out.println("-----------------------------");
			System.out.println("map score: " + mapScore);
			System.out.println("resource score: " + resourceScore);
			System.out.println("-----------------------------\n");

		}

	}

	class RequestLeaderMap extends SequentialBehaviour {

		public RequestLeaderMap(Agent a) {
			super(a);
		}

		public void onStart() {
			String request = GIVE_MAP + "#" + COLONY_1;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myLeader.getName());
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addSubBehaviour(new myReceiver(myAgent, 6 * 1000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- INFORM from "
								+ msg.getSender().getLocalName() + ".  " + SpeechMap.get(GIVE_MAP) + " received");

						try {
							PersonalMapLightWeight freshMap = (PersonalMapLightWeight) msg.getContentObject();
							myMap.mergeMap(freshMap.getMap());
						} catch (Exception e) {

						}
						System.out.println(myAgent.getLocalName() + " <- Map merged");
						// myMap.printMap();
					} else {
						System.out.println(myAgent.getLocalName() + " <- Map merge failed - timeout");
					}
				}
			});
		}
	}

	class GetContacts extends OneShotBehaviour {

		String s_type;
		String my_colony;

		public GetContacts(Agent a, String s_type, String my_colony) {
			super(a);
			this.s_type = s_type;
			this.my_colony = my_colony;
		}

		public void action() {
			DFAgentDescription dfd = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType(s_type);
			if (my_colony != null)
				sd.setOwnership(my_colony);
			dfd.addServices(sd);

			try {
				DFAgentDescription[] res = DFService.search(myAgent, dfd);

				if (res != null) {
					switch (s_type) {
					case WORKER:
						for (DFAgentDescription x : res) {
							if (!x.getName().equals(getAID())) {
								myWorkers.add(x);
							}
						}
						break;
					case LEADER:
						myLeader = res[0];
						break;
					case ENVIRONMENT:
						myEnvironment = res[0];
						break;
					}
				}

			} catch (Exception fe) {
			}

		}
	}

	class HandleRequest extends OneShotBehaviour {
		ACLMessage msg, reply;
		String ConvID, content;

		public HandleRequest(Agent a, ACLMessage msg) {
			super(a);
			this.msg = msg;
			content = msg.getContent();
		}

		public void action() {
			System.out.println(myAgent.getLocalName() + " <- content: " + content);
			String[] req = content.split("#");
			int req_key = Integer.parseInt(req[0]);
			printRequest(req_key, msg);

			switch (req_key) {
			case DEPOSIT_RESOURCES:
				reply = msg.createReply();
				ColonyBANK += Integer.parseInt(req[1]);
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent("" + SUCCESS);
				send(reply);
				break;
			case TRADE:
				reply = msg.createReply();
				if (uniqueRNG.nextDouble() > 0.7) {
					reply.setPerformative(ACLMessage.AGREE);
					int profit = uniqueRNG.nextInt(3);
					System.out.println(myAgent.getLocalName() + " profit: " + profit);
					ColonyBANK += profit;
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
				}

				send(reply);
				break;
			default:
				break;
			}
		}

	} // --- Answer class ---
		// ========== Utility methods =========================

//  --- interacting with DF -------------------

	// prints list of service ADFs
	void printServiceADFs(DFAgentDescription[] arr, String service_type) {
		System.out.print(getLocalName() + " <- ");
		if (arr != null) {
			System.out.println(" found " + arr.length + " " + service_type + " from " + myColony + ":");
			for (DFAgentDescription agent_x : arr) {
				System.out.println(" " + agent_x.getName());
			}
		} else {
			System.out.println(" found " + 0 + " " + service_type + " from " + myColony);
		}
	}

// --- populate SpeechMap
	void populateSpeechMap() {
		// Requests and Replies
		SpeechMap.put(GIVE_MAP, "GIVE_MAP");
		SpeechMap.put(EXPLORE_LOCATION, "EXPLORE_LOCATION");
		SpeechMap.put(MINE_RESOURCES, "MINE_RESOURCES");
		SpeechMap.put(UPDATE_MAP, "UPDATE_MAP");
		SpeechMap.put(GIVE_TASK, "GIVE_TASK");
		SpeechMap.put(FAILURE, "FAILURE");
		SpeechMap.put(SUCCESS, "SUCCESS");
		SpeechMap.put(DEPOSIT_RESOURCES, "DEPOSIT_RESOURCES");

		// Tasks
		SpeechMap.put(STARTUP, "STARTUP");
		SpeechMap.put(EXPLORE, "EXPLORE");
		SpeechMap.put(MINE, "MINE");
		SpeechMap.put(IDLE, "IDLE");
		SpeechMap.put(TRADE, "TRADE");
	}

// --- print REQUEST ------
	void printRequest(int req_key, ACLMessage msg) {
		System.out.println(" - " + this.getLocalName() + " <- REQUEST " + req_key + " from "
				+ msg.getSender().getLocalName() + ": + " + SpeechMap.get(req_key));
	}

//  --- generating Conversation IDs -------------------

	protected static int cidCnt = 0;
	String cidBase;

	String genCID() {
		if (cidBase == null) {
			cidBase = getLocalName() + hashCode() + System.currentTimeMillis() % 10000 + "_";
		}
		return cidBase + (cidCnt++);
	}

//  --- Methods to initialize ACLMessages -------------------

	ACLMessage newMsg(int perf, String content, AID dest) {
		ACLMessage msg = newMsg(perf);
		if (dest != null)
			msg.addReceiver(dest);
		msg.setContent(content);
		return msg;
	}

	ACLMessage newMsg(int perf) {
		ACLMessage msg = new ACLMessage(perf);
		msg.setConversationId(genCID());
		return msg;
	}

//  --- Methods to clean message que -------------------

	static long t0 = System.currentTimeMillis();

	class GCAgent extends TickerBehaviour {
		Set seen = new HashSet(), old = new HashSet();

		GCAgent(Agent a, long dt) {
			super(a, dt);
		}

		protected void onTick() {
			ACLMessage msg = myAgent.receive();
			while (msg != null) {
				if (!old.contains(msg))
					seen.add(msg);
				else {
					System.out.println("==== Flushing message:");
					dumpMessage(msg);
				}
				msg = myAgent.receive();
			}
			for (Iterator it = seen.iterator(); it.hasNext();)
				myAgent.putBack((ACLMessage) it.next());

			old.clear();
			Set tmp = old;
			old = seen;
			seen = tmp;
		}
	}

	void dumpMessage(ACLMessage msg) {
		System.out.print("t=" + (System.currentTimeMillis() - t0) / 1000F + " in " + getLocalName() + ": "
				+ ACLMessage.getPerformative(msg.getPerformative()));
		System.out
				.print("  from: " + (msg.getSender() == null ? "null" : msg.getSender().getLocalName()) + " --> to: ");
		for (Iterator it = msg.getAllReceiver(); it.hasNext();)
			System.out.print(((AID) it.next()).getLocalName() + ", ");
		System.out.println("  cid: " + msg.getConversationId());
		System.out.println("  content: " + msg.getContent());
	}
}