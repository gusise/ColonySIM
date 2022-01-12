package colony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.print.PrintService;
import javax.security.auth.x500.X500Principal;

import colony.Leader.RequestWorkerMap;
import colony.Worker.GetContacts;
import colony.Worker.HandleRequest;
import colony.Worker.ListenForMessages;
import colony.Worker.RequestExplore;
import colony.Worker.RequestMap;
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
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Leader extends Agent implements ColonyVocabulary {

	// Utility variables
	Random rnd = new Random(hashCode());
	Random uniqueRNG = new Random(System.currentTimeMillis());
	MessageTemplate template;
	DFAgentDescription[] result;
	MessageTemplate request = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
	int tickcnt = 0;
	Behaviour flusher;

	// World information
	HashMap<Integer, String> SpeechMap = new HashMap<Integer, String>();
	PersonalMap myMap = null;
	PersonalMapLightWeight map4msg;

	// Colony information
	List<DFAgentDescription> myWorkers = new ArrayList<DFAgentDescription>();
	HashMap<AID, Integer> TaskMap = new HashMap<AID, Integer>();
	DFAgentDescription myBooker;
	DFAgentDescription otherBooker;
	DFAgentDescription myEnvironment;
	String myColony;
	String otherColony;
	String myServiceType;

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

		myServiceType = LEADER;

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
		findAgents.addSubBehaviour(new DelayBehaviour(this, 500));
		findAgents.addSubBehaviour(new GetContacts(this, WORKER, myColony));
		findAgents.addSubBehaviour(new GetContacts(this, BOOKER, myColony));
		findAgents.addSubBehaviour(new GetContacts(this, ENVIRONMENT, null));

		// Listens for Request Messages
		addBehaviour(new ListenForMessages());

		// Start sequence for LeaderAgent
		SequentialBehaviour startLeader = new SequentialBehaviour();
		startLeader.addSubBehaviour(findAgents);
		startLeader.addSubBehaviour(new PrintContacts(this, myEnvironment, ENVIRONMENT));
		startLeader.addSubBehaviour(new PrintContacts(this, myBooker, BOOKER));
		startLeader.addSubBehaviour(new PrintContacts(this, myWorkers, WORKER));
		startLeader.addSubBehaviour(new RequestMap());
		startLeader.addSubBehaviour(new OneShotBehaviour() {

			public void action() {
				ParallelBehaviour giveWorkersTask = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
				myWorkers.forEach(
						w -> giveWorkersTask.addSubBehaviour(new RequestWorkerTask(myAgent, w.getName(), STARTUP)));
				addBehaviour(giveWorkersTask);
			}

		});
//		startLeader.addSubBehaviour(new DelayBehaviour(this, 10*1000));
		startLeader.addSubBehaviour(new StartMainLoop());

		addBehaviour(startLeader);

		// Print all found colony agents after delay
//		addBehaviour(new DelayBehaviour(this, 1000) {
//			protected void handleElapsedTimeout() {
//				SequentialBehaviour sb = new SequentialBehaviour();
//				sb.addSubBehaviour(new PrintContacts(myAgent, myEnvironment, ENVIRONMENT));
//				sb.addSubBehaviour(new PrintContacts(myAgent, myBooker, BOOKER));
//				sb.addSubBehaviour(new PrintContacts(myAgent, myWorkers, WORKER));
//				addBehaviour(sb);
//			}
//		});

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

	boolean exploreRequestSent = false;

	class StartMainLoop extends OneShotBehaviour {

		@Override
		public void action() {

			// Collect maps from workers every 6 seconds
			TickerBehaviour updateMap = new TickerBehaviour(myAgent, 2 * 1000) {

				protected void onTick() {

					if (!TaskMap.containsValue(STARTUP) && !exploreRequestSent) {
						ParallelBehaviour giveWorkersExploreTask = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
						myWorkers.forEach(w -> giveWorkersExploreTask
								.addSubBehaviour(new RequestWorkerTask(myAgent, w.getName(), EXPLORE)));
						addBehaviour(giveWorkersExploreTask);
						exploreRequestSent = true;
					}

					if (TaskMap.containsValue(EXPLORE) && (tickcnt % 5 == 0)) {
						ParallelBehaviour requestWorkerMaps = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);

						for (DFAgentDescription worker : myWorkers) {
							if (TaskMap.get(worker.getName()) != MINE & TaskMap.get(worker.getName()) != TRADE) {
								requestWorkerMaps.addSubBehaviour(new RequestWorkerMap(myAgent, worker.getName()));
							}
						}

						System.out.println(myAgent.getLocalName() + " <- Updating map...");
						addBehaviour(requestWorkerMaps);

						SequentialBehaviour auctionTasks = new SequentialBehaviour();
						if (myMap.hasResourceLocation()) {
							// double roll = uniqueRNG.nextDouble();
							// if (roll > 0.7)
							auctionTasks.addSubBehaviour(new AuctionMiningTask());
						}
						if (myMap.countInstance(otherColony.charAt(2)) > 0) {
							double roll = uniqueRNG.nextDouble();
							if (roll > 0.5)
								auctionTasks.addSubBehaviour(new AuctionTradingTask());
						}
						addBehaviour(auctionTasks);

					}

					tickcnt++;
				}
			};

			// All LOOP actions
			ParallelBehaviour allActions = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
			allActions.addSubBehaviour(updateMap);

			addBehaviour(allActions);
		}

	}

	class AuctionTradingTask extends SequentialBehaviour {

		int bestTradeOffer = 9999;
		ACLMessage bestOffer = null;
		ACLMessage msg = null;
		MessageTemplate template = null;

		public void onStart() {
			String query = "" + TRADE;
			msg = newMsg(ACLMessage.QUERY_REF, query, null);

			template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			ParallelBehaviour waitReplies = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
			System.out.println(myAgent.getLocalName() + " <- getting TRADE offers");

			for (AID worker : TaskMap.keySet()) {
				if (TaskMap.get(worker) != MINE & TaskMap.get(worker) != TRADE)
					msg.addReceiver(worker);
				waitReplies.addSubBehaviour(new myReceiver(myAgent, 1000, template) {
					public void handle(ACLMessage msg) {
						if (msg != null) {
							int offer = Integer.parseInt(msg.getContent());
							System.out.println("Got time " + offer + " from " + msg.getSender().getLocalName());
							if (offer < bestTradeOffer) {
								bestTradeOffer = offer;
								bestOffer = msg;
							}
						}
					}
				});
			}

			addSubBehaviour(waitReplies);
			addSubBehaviour(new DelayBehaviour(myAgent, 100) {
				public void handleElapsedTimeout() {
					if (bestOffer == null) {
						System.out.println("Got no Trade offers");
					} else {
						System.out.println(
								"Best time: " + bestTradeOffer + " ms from: " + bestOffer.getSender().getLocalName());
						if (bestTradeOffer < 2000) {
							addBehaviour(new RequestWorkerTask(myAgent, bestOffer.getSender(), TRADE));
							TaskMap.put(bestOffer.getSender(), TRADE);
						} else {
							System.out.println("Bad offers - postponing TRADE");
						}

					}
				}
			});
			send(msg);
		}

	}

	class AuctionMiningTask extends SequentialBehaviour {

		int bestTime = 9999;
		ACLMessage bestOffer = null;
		ACLMessage msg = null;
		MessageTemplate template = null;

		public void onStart() {
			String query = "" + MINE_RESOURCES;
			msg = newMsg(ACLMessage.QUERY_REF, query, null);

			template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			ParallelBehaviour waitReplies = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
			System.out.println(myAgent.getLocalName() + " <- getting Mining offers");

			for (AID worker : TaskMap.keySet()) {
				if (TaskMap.get(worker) != MINE & TaskMap.get(worker) != TRADE)
					msg.addReceiver(worker);
				waitReplies.addSubBehaviour(new myReceiver(myAgent, 1000, template) {
					public void handle(ACLMessage msg) {
						if (msg != null) {
							int offer = Integer.parseInt(msg.getContent());
							System.out.println("Got time " + offer + " from " + msg.getSender().getLocalName());
							if (offer < bestTime) {
								bestTime = offer;
								bestOffer = msg;
							}
						}
					}
				});
			}

			addSubBehaviour(waitReplies);
			addSubBehaviour(new DelayBehaviour(myAgent, 100) {
				public void handleElapsedTimeout() {
					if (bestOffer == null) {
						System.out.println("Got no Mining offers");
					} else {
						System.out.println(
								"Best time: " + bestTime + " ms from: " + bestOffer.getSender().getLocalName());
						if (bestTime < 2000) {
							addBehaviour(new RequestWorkerTask(myAgent, bestOffer.getSender(), MINE));
							TaskMap.put(bestOffer.getSender(), MINE);
						} else {
							System.out.println("Bad offers - postponing MINE");
							addBehaviour(new DelayBehaviour(myAgent, 1000) {
								protected void handleElapsedTimeout() {
									addBehaviour(new AuctionMiningTask());
								};
							});
						}

					}
				}
			});
			send(msg);
		}

	}

	class ListenForMessages extends CyclicBehaviour {
		@Override
		public void action() {
			ACLMessage msg = receive(request);
			if (msg != null)
				addBehaviour(new HandleRequest(myAgent, msg));
			block();
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
			// System.out.println(myAgent.getLocalName() + " <- content: " + content);
			String[] req = content.split("#");
			int req_key = Integer.parseInt(req[0]);

			switch (req_key) {
			case GIVE_MAP:
				System.out.println(" - " + myAgent.getLocalName() + " <- REQUEST " + GIVE_MAP + " from "
						+ msg.getSender().getLocalName() + ": " + SpeechMap.get(req_key));
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				try {
					map4msg = new PersonalMapLightWeight(myMap.getMap());
					reply.setContentObject(map4msg);
				} catch (Exception e) {
				}
				send(reply);
				break;
			case GIVE_TASK:
				System.out.println(" - " + myAgent.getLocalName() + " <- REQUEST " + GIVE_TASK + " from "
						+ msg.getSender().getLocalName() + ": + " + SpeechMap.get(req_key));
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.AGREE);
				send(reply);
				addBehaviour(new RequestWorkerTask(myAgent, msg.getSender(), EXPLORE));
				break;
			default:
				break;
			}
		}

	} // --- Answer class ---

	class GetContacts extends OneShotBehaviour {

		String s_type;
		String colony;

		public GetContacts(Agent a, String s_type, String colony) {
			super(a);
			this.s_type = s_type;
			this.colony = colony;
		}

		public void action() {
			DFAgentDescription dfd = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType(s_type);
			if (colony != null)
				sd.setOwnership(colony);
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
					case BOOKER:
						if (colony.equals(myColony))
							myBooker = res[0];
						else
							otherBooker = res[0];
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

	// Request Worker performs task
	class RequestWorkerTask extends OneShotBehaviour {

		AID worker;
		int task;

		public RequestWorkerTask(Agent a, AID worker, int task) {
			super(a);
			this.task = task;
			this.worker = worker;
		}

		public void action() {
			System.out.println("Sending task REQUEST " + SpeechMap.get(task));
			String request = GIVE_TASK + "#" + task;
			if (task == STARTUP) {
				TaskMap.put(worker, STARTUP);
			}
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, worker);
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addBehaviour(new myReceiver(myAgent, 10 * 1000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- INFORM from "
								+ msg.getSender().getLocalName() + ".  " + SpeechMap.get(task) + " success");

						switch (task) {
						case EXPLORE:
							TaskMap.put(worker, EXPLORE);
							break;
						case STARTUP:
							TaskMap.put(worker, IDLE);
							break;
						case MINE:
							TaskMap.put(worker, MINE);
							break;
						case TRADE:
							TaskMap.put(worker, TRADE);
						default:
							break;
						}

					} else {
						System.out.println(
								myAgent.getLocalName() + " <- " + SpeechMap.get(task) + "  REQUEST failed - timeout");
					}
				}
			});
		}
	}

	// Get map from a Worker
	class RequestWorkerMap extends SequentialBehaviour {

		AID worker;

		public RequestWorkerMap(Agent a, AID worker) {
			super(a);
			this.worker = worker;
		}

		public void onStart() {
			String request = GIVE_MAP + "#" + COLONY_1;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, worker);
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addSubBehaviour(new myReceiver(myAgent, 3000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- INFORM from "
								+ msg.getSender().getLocalName() + ".  " + GIVE_MAP + "map req. success");

						try {
							PersonalMapLightWeight freshMap = (PersonalMapLightWeight) msg.getContentObject();
							myMap.mergeMap(freshMap.getMap());
						} catch (Exception e) {

						}
						System.out.println(myAgent.getLocalName() + " <- Map merged");
						myMap.printMap();
					} else {
						System.out.println(myAgent.getLocalName() + " <- Map merge failed - timeout");
					}
				}
			});
		}
	}

	// ========== Utility methods =========================

//  --- interacting with DF -------------------

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

		// Tasks
		SpeechMap.put(STARTUP, "STARTUP");
		SpeechMap.put(EXPLORE, "EXPLORE");
		SpeechMap.put(MINE, "MINE");
		SpeechMap.put(IDLE, "IDLE");
		SpeechMap.put(TRADE, "TRADE");
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
