package ai.abstraction;

/**
 * @Author: Wenhang Chen
 * @Description: RTS_AI
 * @Date: Created in 16:11 10/22/2019
 * @Modified by:
 */

import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.BFSPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import rts.*;
import rts.units.*;

/*
 * 算法说明：对战场局势进行分析，并根据战场形势采取不同策略
 */
public class MyRobot extends AbstractionLayerAI {
    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType heavyType;

    List<Long> preResource = new ArrayList<>();    // 无效资源列表ID，用于解决死锁问题

    public MyRobot(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }

    public MyRobot(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
        super.reset();
    }

    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        if (utt != null) {
            workerType = utt.getUnitType("Worker");
            baseType = utt.getUnitType("Base");
            barracksType = utt.getUnitType("Barracks");
            heavyType = utt.getUnitType("Heavy");
        }
    }

    public AI clone() {
        return new MyRobot(utt, pf);
    }

    /*
     * 1. 此函数会一直循环执行
     * 2. 如果某处逻辑比较复杂，例如多层嵌套循环，并不会导致己方单方面决策缓慢，
     *    而是会降低整个游戏的运行速度，也就是说逻辑的复杂程度是不会导致劣势的
     * 3. 每回合只能进行一次操作，多次操作后面的会覆盖前面的
     */
    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
        // 获取回合数
        // System.out.println("LightRushAI for player " + player + " (cycle " + gs.getTime() + ")");

        List<Unit> myBases = new ArrayList<>();       // 我方基地列表
        List<Unit> myBarracks = new ArrayList<>();    // 我方兵营列表
        List<Unit> myMeleeUnit = new ArrayList<>();   // 我方可攻击单位列表
        List<Unit> myWorkers = new ArrayList<>();     // 我方worker列表

        List<Unit> enemyBases = new ArrayList<>();     // 敌方基地列表
        List<Unit> enemyBarracks = new ArrayList<>();  // 敌方兵营列表
        List<Unit> enemyMeleeUnit = new ArrayList<>(); // 敌方攻击单位列表
        List<Unit> enemyWorkers = new ArrayList<>();   // 敌方worker列表

        // 按照类型归类
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType) {
                if (u.getPlayer() == player) {
                    myBases.add(u);
                } else {
                    enemyBases.add(u);
                }
            }
        }
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == barracksType) {
                if (u.getPlayer() == player) {
                    myBarracks.add(u);
                } else {
                    enemyBarracks.add(u);
                }
            }
        }
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest &&
                    u.getPlayer() == player) {
                myMeleeUnit.add(u);
            }
        }
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest &&
                    u.getPlayer() == player) {
                myWorkers.add(u);
            }
        }

        // 开始分析战场局势
        analyzeWar(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);

        return translateActions(player, gs);
    }

    // 评估战场形式，并根据评估结果采取不同策略
    public void analyzeWar(GameState gs, PhysicalGameState pgs, Player p,
                           List<Unit> myBases,
                           List<Unit> myBarracks,
                           List<Unit> myMeleeUnit,
                           List<Unit> myWorkers,
                           List<Unit> enemyBases) {
        // 双方基地之间的距离
        double distanceOfBases = 0;

        // 判断还有没有基地
        if (myBases.size() == 0 && enemyBases.size() == 0) {
            System.out.println("双方家都没了");
        } else if (myBases.size() == 0) {
            System.out.println("我们家没了");
        } else if (enemyBases.size() == 0) {
            System.out.println("对面家没了");
        } else {
            // 获取两个基地之间的距离，目前只对第一个基地进行计算
            distanceOfBases = Math.sqrt(Math.pow(myBases.get(0).getX() - enemyBases.get(0).getX(), 2) + Math.pow(myBases.get(0).getY() - enemyBases.get(0).getY(), 2));
        }

        if (distanceOfBases < 16) {
            // 如果距离很小，采取偷家策略
            stealHomeTactics(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);
        } else if (distanceOfBases < 30) {
            stealHomeTactics(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);
        } else if (distanceOfBases < 46) {
            // 如果距离较小，采取双矿工workRush策略
            workRushTactics(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);
        } else {
            // 如果距离够远，有足够的时间建造兵营，那么建造兵营，让heavy攻击，双矿工
            heavyRushTactics(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);
        }
    }

    // 偷家策略
    public void stealHomeTactics(GameState gs, PhysicalGameState pgs, Player p,
                                 List<Unit> myBases,
                                 List<Unit> myBarracks,
                                 List<Unit> myMeleeUnit,
                                 List<Unit> myWorkers,
                                 List<Unit> enemyBases) {
        // 安排起来
        for (Unit u : myBases) {
            // 如果没任务
            if (gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, false, 1);
    }

    public void workRushTactics(GameState gs, PhysicalGameState pgs, Player p,
                                List<Unit> myBases,
                                List<Unit> myBarracks,
                                List<Unit> myMeleeUnit,
                                List<Unit> myWorkers,
                                List<Unit> enemyBases) {
        // 安排起来
        for (Unit u : myBases) {
            // 如果没任务
            if (gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, false, 2);
    }

    public void heavyRushTactics(GameState gs, PhysicalGameState pgs, Player p,
                                 List<Unit> myBases,
                                 List<Unit> myBarracks,
                                 List<Unit> myMeleeUnit,
                                 List<Unit> myWorkers,
                                 List<Unit> enemyBases) {
        // 安排起来
        for (Unit u : myBases) {
            if (gs.getActionAssignment(u) == null) {
                // 至少有两个worker，一个采矿，一个造兵营
                if (myWorkers.size() < 2) {
                    baseBehavior(u, p, pgs);
                }
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, true, 2);
    }

    // TODO 韬光养晦模式，死锁时可能要调用此函数
    public void prepareTactics(GameState gs, PhysicalGameState pgs, Player p,
                               List<Unit> myBases,
                               List<Unit> myBarracks,
                               List<Unit> myMeleeUnit,
                               List<Unit> myWorkers,
                               List<Unit> enemyBases) {
        // 安排起来
        for (Unit u : myBases) {
            if (gs.getActionAssignment(u) == null) {
                // 至少有两个worker，一个采矿，一个造兵营
                if (myWorkers.size() < 2) {
                    baseBehavior(u, p, pgs);
                }
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, true, -1);
    }


    // 基地行为
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= workerType.cost) train(u, workerType);
    }

    // 兵营行为
    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= heavyType.cost) {
            train(u, heavyType);
        }
    }

    // 攻击单位行为
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
//        for (Unit u2 : pgs.getUnits()) {
//            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
//                if (u2.getType() == baseType) {
//                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
//                    if (d < 5) {
//                        closestEnemy = u2;
//                        closestDistance = d;
//                    }
//                }
//            }
//        }
        if (closestEnemy != null) {
            attack(u, closestEnemy);
        } else {
            // 如果有战争迷雾，那就展开地毯式搜索
            for (int i = 0; i < pgs.getWidth(); i++) {
                for (int j = 0; j < pgs.getHeight(); j += 3) {
                    move(u, i, j);
                }
            }
        }
    }

    // worker行为
    // 如果没有base，尝试建造，如果有要求建造兵营，则建造兵营，闲杂人等攻击
    public void workersBehavior(List<Unit> workers, Player p, GameState gs, boolean buildBarracks, int harvestNum) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int nbases = 0;
        int resourcesUsed = 0;
        Unit harvestWorker = null;
        Unit buildBasesWorker = null;
        Unit buildBarracksWorker = null;
        List<Unit> freeWorkers = new LinkedList<Unit>(workers);

        if (workers.isEmpty()) return;

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType &&
                    u2.getPlayer() == p.getID()) nbases++;
        }

        // 如果没有base，尝试建造
        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
            // 如果资源数大于base单位的消耗和本回合之前的消耗数之和
            if (p.getResources() >= baseType.cost + resourcesUsed) {
                buildBasesWorker = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(buildBasesWorker, baseType, buildBasesWorker.getX(), buildBasesWorker.getY(), reservedPositions, p, pgs);
                resourcesUsed += baseType.cost;
            }
        }

        if (freeWorkers.size() > 0) harvestWorker = freeWorkers.remove(0);
        // 矿工行为
        if (harvestWorker != null) {
            harvestBehavior(harvestWorker, p, gs);
        }

        // 建造兵营
        if (buildBarracks) {
            if (freeWorkers.size() > 0) {
                buildBarracksWorker = freeWorkers.remove(0);
                // 如果资源够就造兵营
                if (p.getResources() >= barracksType.cost + resourcesUsed) {
                    buildIfNotAlreadyBuilding(buildBarracksWorker, barracksType, buildBarracksWorker.getX(), buildBarracksWorker.getY(), reservedPositions, p, pgs);
                } else {
                    // 否则先去挖矿
                    harvestBehavior(buildBarracksWorker, p, gs);
                }
            }
        }

        if (harvestNum == -1) {
            // 如果harvestNum==-1，则采取韬光养晦模式，全民挖矿积累资源，同时造兵
            while (freeWorkers.size() > 0) {
                harvestWorker = freeWorkers.remove(0);
                // 如果还有能挖矿的
                if (harvestWorker != null) {
                    harvestBehavior(harvestWorker, p, gs);
                }
            }
        } else {
            for (int i = 0; i < harvestNum - 1; i++) {
                if (freeWorkers.size() > 0) {
                    harvestWorker = freeWorkers.remove(0);
                    // 如果还有能挖矿的
                    if (harvestWorker != null) {
                        harvestBehavior(harvestWorker, p, gs);
                    }
                } else {
                    break;
                }
            }
        }

        for (Unit u : freeWorkers) meleeUnitBehavior(u, p, gs);
    }

    // 矿工行为
    public void harvestBehavior(Unit harvestWorker, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestBase = null;
        Unit closestResource = null;
        int closestDistance = 0;
        boolean lock = true;


        // TODO 尝试解决死锁问题，未成功
        List<Unit> unitList = pgs.getUnits();
        for (int i = 0; i < unitList.size(); i++) {
            Unit u = unitList.get(i);
            if (u.getPlayer() == p.getID()) {
                UnitActionAssignment uaa = gs.getActionAssignment(u);
                if (uaa == null) lock = false;
                if (uaa != null && uaa.action.getType() != UnitAction.TYPE_NONE) {
                    preResource.clear();
                    lock = false;
                }
            }
        }


        // 想办法去最近的资源处挖矿
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType().isResource) {
                if (!lock || !preResource.contains(u2.getID())) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
        }

        closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) {
                int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                if (closestBase == null || d < closestDistance) {
                    closestBase = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestResource != null && closestBase != null) {
            AbstractAction aa = getAbstractAction(harvestWorker);
            if (aa instanceof Harvest) {
                Harvest h_aa = (Harvest) aa;
                if (h_aa.target != closestResource || h_aa.base != closestBase) {
                    harvest(harvestWorker, closestResource, closestBase);
                    if (!preResource.contains(closestResource.getID()))
                        preResource.add(closestResource.getID());
                }
            } else {
                harvest(harvestWorker, closestResource, closestBase);
                if (!preResource.contains(closestResource.getID()))
                    preResource.add(closestResource.getID());
            }
        } else if (closestResource == null) {
            // 前面已经判断过能否建造基地了，所以此时必然是没有资源同时没有基地的窘境
            // 进退维谷，四面楚歌，此时不杀，更待何时？！
            // 杀！
            meleeUnitBehavior(harvestWorker, p, gs);
        }
    }


    // 工具类——实验证明死锁根本原因在于挖矿函数
    // 判定死锁，并返回当前状态是否死锁
//    public boolean removeLock(Player p, GameState gs) {
//        PhysicalGameState pgs = gs.getPhysicalGameState();
//        boolean flag = false;
//        Unit base = null;
//        int aroundBase = 0;
//        for (Unit u : pgs.getUnits()) {
//            if (u.getType() == baseType &&
//                    u.getPlayer() == p.getID()) {
//                base = u;
//            }
//        }
//
//        for (Unit u : pgs.getUnits()) {
//            if (u.getPlayer() == p.getID()) {
//                if (base != null) {
//                    if (u.getX() == base.getX() + 1 && u.getY() == base.getY()
//                            || u.getX() == base.getX() - 1 && u.getY() == base.getY()
//                            || u.getX() == base.getX() && u.getY() == base.getY() + 1
//                            || u.getX() == base.getX() && u.getY() == base.getY() - 1) {
//                        // 如果围满了，则疏通
//                        if (aroundBase >= 3) {
//                            flag = true;
//                            int x = (int) (1 + Math.random() * (pgs.getWidth() + 1));
//                            int y = (int) (1 + Math.random() * (pgs.getHeight() + 1));
//                            move(u, x, y);
//                            // 如果有战争迷雾，那就展开地毯式搜索
////                            for (int i = 0; i < pgs.getWidth(); i++) {
////                                for (int j = 0; j < pgs.getHeight(); j += 3) {
//////                                    System.out.println(i + "   " + j);
////                                    move(u, i, j);
////                                }
////                            }
//                            aroundBase--;
//                        }
//                        aroundBase++;
//                    }
//                }
//            }
//        }
//        return flag;
//    }


    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }
}
