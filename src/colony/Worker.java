package colony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.text.Style;

import colony.Enviro.HandleRequest;
import colony.Leader.GCAgent;
import colony.Leader.RequestWorkerMap;
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

public class Worker extends Agent implements ColonyVocabulary {

	// Utility variables
	Random rnd = new Random(hashCode());
	Random uniqueRNG = new Random(System.currentTimeMillis());
	MessageTemplate template;
	DFAgentDescription[] result;
	MessageTemplate requestORquery = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
			MessageTemplate.MatchPerformative(ACLMessage.QUERY_REF));
	int timeDelayOnStartup = 2000;
	Behaviour flusher;
	SequentialBehaviour startWorker;
	int closed_mines_cnt = 0;
	int myMiningOffer = 9999;
	int myBank = 0;
	int myTradeOffer = 9999;
	boolean readyToMine = false;
	boolean doneMining = false;
	boolean tradeDone = false;
	boolean currentlyMining = false;
	boolean currentlyTrading = false;
	String currentMine = null;
	boolean raiseAbortFlag = false;

	// World information
	HashMap<Integer, String> SpeechMap = new HashMap<Integer, String>();
	HashMap<Integer, String> ClosedMines = new HashMap<Integer, String>();
	PersonalMap myMap = null;
	PersonalMapLightWeight map4msg;
	int last_explore_x;
	int last_explore_y;

	// Colony information
	List<DFAgentDescription> knownWorkers = new ArrayList<DFAgentDescription>();
	List<String> minedOut = new ArrayList<String>();
	DFAgentDescription myBooker;
	DFAgentDescription otherBooker;
	DFAgentDescription myLeader;
	DFAgentDescription myEnvironment;
	String myColony;
	String otherColony;
	String myServiceType;
	int myCurrentTask = IDLE;

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

		myServiceType = WORKER;

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
		findAgents.addSubBehaviour(new GetContacts(this, BOOKER, myColony));
		findAgents.addSubBehaviour(new GetContacts(this, BOOKER, otherColony));
		findAgents.addSubBehaviour(new GetContacts(this, ENVIRONMENT, null));

		startWorker = new SequentialBehaviour();
		startWorker.addSubBehaviour(findAgents);
		startWorker.addSubBehaviour(new RequestMap());
		startWorker.addSubBehaviour(new DelayBehaviour(this, timeDelayOnStartup));

		// MAIN WORK LOOP
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				// Perform one action every second
				TickerBehaviour doTask = new TickerBehaviour(myAgent, 1 * 1000) {

					protected void onTick() {
						switch (myCurrentTask) {
						case STARTUP:
							addBehaviour(startWorker);
							myCurrentTask = IDLE;
							break;

						case EXPLORE:
							addBehaviour(new Explore());
							break;

						case MINE:
							resetExplorationLocation();
							if (!readyToMine & !raiseAbortFlag)
								addBehaviour(new PrepeareMiningOperation());
							if (readyToMine & !doneMining & !currentlyMining & !raiseAbortFlag)
								addBehaviour(new MineResources(myAgent, currentMine));
							if ((doneMining & !currentlyMining) || raiseAbortFlag) {
								myCurrentTask = IDLE;
								readyToMine = false;
								currentlyMining = false;
								doneMining = false;
								addBehaviour(new RequestTaskFromLeader());
								addBehaviour(new RequestDepositResources());
							}
							break;
						case TRADE:
							resetExplorationLocation();
							if (!currentlyTrading & !tradeDone) {
								addBehaviour(new RequestTrade());
								currentlyTrading = true;
							}

							if (tradeDone) {
								myCurrentTask = IDLE;
								currentlyTrading = false;
								tradeDone = false;
								addBehaviour(new RequestTaskFromLeader());
							}

							break;

						case IDLE:
							System.out.println(myAgent.getLocalName() + " <- Idiling...");
							break;
						}
					}
				};

				addBehaviour(doTask);
			}
		});

		addBehaviour(new ListenForMessages());

//		addBehaviour(new SelfDestruct(this, 1000));

		// Print all found colony agents after delay
//		addBehaviour(new DelayBehaviour(this, 1000) {
//			protected void handleElapsedTimeout() {
//				SequentialBehaviour sb = new SequentialBehaviour();
//				sb.addSubBehaviour(new PrintContacts(myAgent, myEnvironment, ENVIRONMENT));
//				sb.addSubBehaviour(new PrintContacts(myAgent, myLeader, LEADER));
//				sb.addSubBehaviour(new PrintContacts(myAgent, myBooker, BOOKER));
//				sb.addSubBehaviour(new PrintContacts(myAgent, knownWorkers, WORKER));
//				addBehaviour(sb);
//			}
//		});

	}// setup

	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (Exception e) {
			// TODO: handle exception
		}
		// System.exit(0);
	}

	class MineResources extends SequentialBehaviour {

		String mine;
		ACLMessage msg;
		MessageTemplate template;

		public MineResources(Agent a, String mine) {
			super(a);
			this.mine = mine;
			currentlyMining = true;
		}

		@Override
		public void onStart() {
			String request = MINE_RESOURCES + "#" + mine;
			msg = newMsg(ACLMessage.REQUEST, request, myEnvironment.getName());

			template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addSubBehaviour(new DelayBehaviour(myAgent, myMiningOffer));

			addSubBehaviour(new OneShotBehaviour() {
				public void action() {
					send(msg);
				}
			});

			addSubBehaviour(new myReceiver(myAgent, 3 * 1000, template) {
				@Override
				public void handle(ACLMessage msg) {
					if (msg != null) {
						int result = Integer.parseInt(msg.getContent());
						System.out.println(myAgent.getLocalName() + " <- " + SpeechMap.get(MINE_RESOURCES) + " "
								+ SpeechMap.get(result));
						switch (result) {
						case SUCCESS:
							myBank += 1;
							break;
						case FAILURE:
							doneMining = true;
							break;
						default:
							break;
						}
						currentlyMining = false;
					}
				}
			});

		}

	}

	class PrepeareMiningOperation extends SequentialBehaviour {

		ACLMessage msg = null;

		public void onStart() {
			currentMine = myMap.getOpenMine();
			if (currentMine == null) {
				raiseAbortFlag = true;
				return;
			}

			ClosedMines.put(1, currentMine);
			closed_mines_cnt++;

			ParallelBehaviour waitReplies = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);

			System.out.println("Sending REQUEST " + SpeechMap.get(UPDATE_MAP));
			String request = UPDATE_MAP + "#" + currentMine;

			msg = newMsg(ACLMessage.REQUEST, request, null);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			for (DFAgentDescription worker : knownWorkers) {
				msg.addReceiver(worker.getName());
				waitReplies.addSubBehaviour(new myReceiver(myAgent, 1000, template) {
					@Override
					public void handle(ACLMessage msg) {
						if (msg != null) {
							int key = Integer.parseInt(msg.getContent());
							System.out.println(myAgent.getLocalName() + " <- closeMine " + SpeechMap.get(key) + " from "
									+ msg.getSender().getLocalName());
						}
					}
				});
			}
			addSubBehaviour(waitReplies);
			addSubBehaviour(new DelayBehaviour(myAgent, 1500) {
				@Override
				protected void handleElapsedTimeout() {
					readyToMine = true;
				}
			});
			send(msg);

		}
	}

	class HandleQuerry extends OneShotBehaviour {

		ACLMessage msg, reply;
		String ConvID, content;

		public HandleQuerry(Agent a, ACLMessage msg) {
			super(a);
			this.msg = msg;
			content = msg.getContent();

		}

		@Override
		public void action() {
			switch (Integer.parseInt(content)) {
			case MINE_RESOURCES:
				addBehaviour(new MiningTaskApplication(myAgent, msg));
				break;
			case TRADE:
				addBehaviour(new TradeTaskApplication(myAgent, msg));
			default:
				break;
			}
		}
	}

	class TradeTaskApplication extends SequentialBehaviour {
		ACLMessage msg, reply;
		String ConvID;

		public TradeTaskApplication(Agent a, ACLMessage msg) {
			super(a);
			this.msg = msg;
			ConvID = msg.getConversationId();
			myTradeOffer = 500 + uniqueRNG.nextInt(10) * 100;
		}

		public void onStart() {
			System.out.println(" - " + myAgent.getLocalName() + " <- QUERY from " + msg.getSender().getLocalName()
					+ ".  sending offer: " + myTradeOffer + " ms");

			reply = msg.createReply();
			reply.setPerformative(ACLMessage.INFORM);
			reply.setContent("" + myTradeOffer);
			send(reply);

		}

	}

	class MiningTaskApplication extends SequentialBehaviour {
		ACLMessage msg, reply;
		String ConvID;

		public MiningTaskApplication(Agent a, ACLMessage msg) {
			super(a);
			this.msg = msg;
			ConvID = msg.getConversationId();
			myMiningOffer = 500 + uniqueRNG.nextInt(30) * 100;
		}

		public void onStart() {
			System.out.println(" - " + myAgent.getLocalName() + " <- QUERY from " + msg.getSender().getLocalName()
					+ ".  sending offer: " + myMiningOffer + " ms");

			reply = msg.createReply();
			reply.setPerformative(ACLMessage.INFORM);
			reply.setContent("" + myMiningOffer);
			send(reply);

		}

	}

	class DelayedReply extends OneShotBehaviour {
		ACLMessage reply;

		public DelayedReply(Agent a, ACLMessage reply) {
			super(a);
			this.reply = reply;
		}

		@Override
		public void action() {
			send(reply);
		}

	}

	class Explore extends OneShotBehaviour {

		@Override
		public void action() {
			boolean new_location = false;
			if (!myMap.hasUnexploredLocation()) {
				myCurrentTask = IDLE;
				return;
			}
			int lessCnt = 0;
			while (!new_location) {

				switch (10 * (uniqueRNG.nextInt(4) + 1)) {
				case UP:
					last_explore_y -= (last_explore_y == 0) ? 0 : 1;
					break;
				case DOWN:
					last_explore_y += (last_explore_y == myMap.getMapYdim() - 1) ? 0 : 1;
					break;
				case LEFT:

					if (myColony == "K1") {
						if (lessCnt > 2) {
							last_explore_x -= (last_explore_x == 0) ? 0 : 1;
							lessCnt = 0;
						}
						lessCnt += 1;
					} else {
						last_explore_x -= (last_explore_x == 0) ? 0 : 1;
					}
					break;
				case RIGHT:
					if (myColony == "K2") {
						if (lessCnt > 2) {
							last_explore_x += (last_explore_x == myMap.getMapXdim() - 1) ? 0 : 1;
							lessCnt = 0;
						}
						lessCnt += 1;
					} else {
						last_explore_x += (last_explore_x == myMap.getMapXdim() - 1) ? 0 : 1;
					}
					break;
				}
				if (myMap.getLocation(last_explore_x, last_explore_y) == '?')
					new_location = true;
			}
			addBehaviour(new RequestExplore(myAgent, last_explore_x, last_explore_y));

		}

	}

	class RequestLeaderMap extends OneShotBehaviour {

		public RequestLeaderMap(Agent a) {
			super(a);
		}

		public void action() {
			String request = GIVE_MAP + "#" + COLONY_1;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myLeader.getName());
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addBehaviour(new myReceiver(myAgent, 4 * 1000, template) {

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
						myMap.printMap();
					} else {
						System.out.println(myAgent.getLocalName() + " <- Map merge failed - timeout");
					}
				}
			});
		}
	}

	class ListenForMessages extends CyclicBehaviour {
		@Override
		public void action() {
			ACLMessage msg = receive(requestORquery);
			if (msg != null) {
				if (msg.getPerformative() == ACLMessage.QUERY_REF)
					addBehaviour(new HandleQuerry(myAgent, msg));
				if (msg.getPerformative() == ACLMessage.REQUEST)
					addBehaviour(new HandleRequest(myAgent, msg));
			}
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
			System.out.println(myAgent.getLocalName() + " <- content: " + content);
			String[] req = content.split("#");
			int req_key = Integer.parseInt(req[0]);

			printRequest(req_key, msg);

			switch (req_key) {
			case GIVE_MAP:
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				try {
					map4msg = new PersonalMapLightWeight(myMap.getMap());
					reply.setContentObject(map4msg);
				} catch (Exception e) {
				}
				send(reply);

				// Ask for leaders map after he has combined them
				addBehaviour(new DelayBehaviour(myAgent, 2000) {
					@Override
					protected void handleElapsedTimeout() {
						addBehaviour(new RequestLeaderMap(myAgent));
					}
				});
				break;
			case GIVE_TASK:
				myCurrentTask = Integer.parseInt(req[1]);
				System.out.println("Setting task: " + SpeechMap.get(myCurrentTask));
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent("" + SUCCESS + "#" + timeDelayOnStartup);
				if (myCurrentTask == STARTUP)
					startWorker.addSubBehaviour(new DelayedReply(myAgent, reply));
				else
					send(reply);
				break;
			case UPDATE_MAP:
				int x = Integer.parseInt(req[1]);
				int y = Integer.parseInt(req[2]);
				String mine = x + "#" + y;
				System.out.println("Closing mine: " + mine);
				myMap.updateMap(x, y, CLOSED_MINE);
				ClosedMines.put(closed_mines_cnt, mine);
				closed_mines_cnt++;

				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent("" + SUCCESS);

				send(reply);
				break;
			default:
				break;
			}
		}

	} // --- Answer class ---

	// Get map from a Worker
	class RequestTaskFromLeader extends OneShotBehaviour {

		public void action() {
			System.out.println(myAgent.getLocalName() + "<- " + SpeechMap.get(MINE_RESOURCES) + " finished");
			System.out.println(myAgent.getLocalName() + "<- Sending new Task request");
			String request = GIVE_TASK + "";
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myLeader.getName());
			send(msg);

			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.AGREE),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addBehaviour(new myReceiver(myAgent, 2 * 1000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- AGREE from "
								+ msg.getSender().getLocalName() + " REQUEST " + SpeechMap.get(GIVE_TASK) + " success");
					} else {
						System.out.println(myAgent.getLocalName() + " <- REQUEST " + SpeechMap.get(GIVE_TASK)
								+ " failed - timeout");
					}
				}
			});
		}
	}

	class RequestDepositResources extends SequentialBehaviour {

		public void onStart() {
			System.out.println(myAgent.getLocalName() + "<- " + SpeechMap.get(DEPOSIT_RESOURCES));
			String request = DEPOSIT_RESOURCES + "#" + myBank;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myBooker.getName());
			send(msg);

			MessageTemplate template = MessageTemplate.and(
					MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.AGREE),
							MessageTemplate.MatchPerformative(ACLMessage.REFUSE)),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addSubBehaviour(new myReceiver(myAgent, 2 * 1000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						if (msg.getPerformative() == ACLMessage.AGREE) {
							System.out.println(" - " + myAgent.getLocalName() + " <- AGREE from "
									+ msg.getSender().getLocalName() + " " + SpeechMap.get(DEPOSIT_RESOURCES));
							myBank = 0;
						} else if (msg.getPerformative() == ACLMessage.REFUSE) {
							System.out.println(" - " + myAgent.getLocalName() + " <- REFUSE from "
									+ msg.getSender().getLocalName() + " " + SpeechMap.get(DEPOSIT_RESOURCES));
						}
					} else {
						System.out.println(myAgent.getLocalName() + " <- REQUEST " + SpeechMap.get(GIVE_TASK)
								+ " failed - timeout");
					}
				}
			});
		}
	}

	class RequestTrade extends SequentialBehaviour {

		public void onStart() {
			System.out.println(myAgent.getLocalName() + "<- " + SpeechMap.get(TRADE));
			String request = TRADE + "";
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, otherBooker.getName());

			MessageTemplate template = MessageTemplate.and(
					MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.AGREE),
							MessageTemplate.MatchPerformative(ACLMessage.REFUSE)),
					MessageTemplate.MatchConversationId(msg.getConversationId()));
			addSubBehaviour(new DelayBehaviour(myAgent, myTradeOffer));

			addSubBehaviour(new OneShotBehaviour() {

				public void action() {
					send(msg);
				}
			});

			addSubBehaviour(new myReceiver(myAgent, 2 * 1000, template) {

				public void handle(ACLMessage msg) {

					if (msg != null) {
						if (msg.getPerformative() == ACLMessage.AGREE) {
							System.out.println(myAgent.getLocalName() + " <- AGREE from "
									+ msg.getSender().getLocalName() + " " + SpeechMap.get(DEPOSIT_RESOURCES));
							int profit = uniqueRNG.nextInt(4);
							System.out.println(myAgent.getLocalName() + " profit: " + profit);
							myBank += profit;
							addBehaviour(new RequestDepositResources());
						} else if (msg.getPerformative() == ACLMessage.REFUSE) {
							System.out.println(myAgent.getLocalName() + " <- REFUSE from "
									+ msg.getSender().getLocalName() + " " + SpeechMap.get(DEPOSIT_RESOURCES));
						}
						tradeDone = true;
					} else {
						System.out.println(myAgent.getLocalName() + " <- REQUEST " + SpeechMap.get(GIVE_TASK)
								+ " failed - timeout");
						tradeDone = true;
					}
				}
			});
		}
	}

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
								knownWorkers.add(x);
							}
						}
						break;
					case LEADER:
						myLeader = res[0];
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

	class RequestMap extends OneShotBehaviour {
		public void action() {
			String request = GIVE_MAP + "#" + COLONY_1;
			ACLMessage msg = newMsg(ACLMessage.REQUEST, request, myEnvironment.getName());
			send(msg);
			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			addBehaviour(new myReceiver(myAgent, -1, template) {

				public void handle(ACLMessage msg) {
					if (msg != null) {
						System.out.println(" - " + myAgent.getLocalName() + " <- INFORM from "
								+ msg.getSender().getLocalName() + ".  " + SpeechMap.get(GIVE_MAP) + " success");

						try {
							PersonalMapLightWeight freshMap = (PersonalMapLightWeight) msg.getContentObject();
							myMap = new PersonalMap(freshMap.getMap());
						} catch (Exception e) {
							// TODO: handle exception
						}
						System.out.print("Got passed map receive");

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
						resetExplorationLocation();
						System.out.print(myAgent.getLocalName() + " <- Map gen success:");
						myMap.printMap();

					} else {
						System.out.println(myAgent.getLocalName() + " <- Map gen fail");
					}
				}
			});
		}
	}

	class RequestExplore extends SimpleBehaviour {

		boolean finished = false;
		String request;
		ACLMessage msg;
		int x;
		int y;

		public RequestExplore(Agent a, int x, int y) {
			super(a);
			this.x = x;
			this.y = y;
		}

		public void action() {
			if (myMap == null)
				return;

			this.request = EXPLORE_LOCATION + "#" + x + "#" + y;
			msg = newMsg(ACLMessage.REQUEST, request, myEnvironment.getName());
			send(msg);
			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId(msg.getConversationId()));

			finished = true;
			addBehaviour(new myReceiver(myAgent, 3000, template) {

				public void handle(ACLMessage msg) {
					if (msg != null) {
						System.out.println(
								"\n - " + myAgent.getLocalName() + " <- INFORM from " + msg.getSender().getLocalName()
										+ ".  " + SpeechMap.get(EXPLORE_LOCATION) + " result: " + msg.getContent());

						String[] rep = msg.getContent().split("#");

						int x = Integer.parseInt(rep[1]);
						int y = Integer.parseInt(rep[2]);

						myMap.updateMap(x, y, rep[0].charAt(0));

						System.out.print(myAgent.getLocalName() + " <- Updated map:");
						myMap.printMap();

					} else {
						System.out.println(myAgent.getLocalName() + " <- Map update fail");
					}
				}
			});
		}

		@Override
		public boolean done() {
			return finished;
		}

	}

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

//  --- reset Exploration Locations -------------------
	void resetExplorationLocation() {

		switch (myColony.charAt(2)) {
		case COLONY_1:
			last_explore_x = 0;
			last_explore_y = myMap.getMapYdim() / 2;
			break;
		case COLONY_2:
			last_explore_x = myMap.getMapXdim() - 1;
			last_explore_y = myMap.getMapYdim() / 2;
			break;
		}

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
