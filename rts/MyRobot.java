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

import java.util.*;

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
    UnitType lightType;

    // 对于某个单位而言的无效资源列表ID，用于解决死锁问题
    HashMap<Long, List<Long>> preResource = new HashMap<Long, List<Long>>();

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
            lightType = utt.getUnitType("Light");
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

//        // 如果发生死锁，优先解决死锁
//        if (isLock(p, gs)) {
//            // 采取韬光养晦策略
//            prepareTactics(gs, pgs, p, myBases, myBarracks, myMeleeUnit, myWorkers, enemyBases);
//            return;
//        }

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
        } else if (distanceOfBases < 20) {
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
            if (gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs, heavyType);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, false, 1);
    }

    // 稍微有点脑子的workerRush策略
    public void workRushTactics(GameState gs, PhysicalGameState pgs, Player p,
                                List<Unit> myBases,
                                List<Unit> myBarracks,
                                List<Unit> myMeleeUnit,
                                List<Unit> myWorkers,
                                List<Unit> enemyBases) {
        // 安排起来
        for (Unit u : myBases) {
            if (gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs, heavyType);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, false, 2);
    }

    // 主要攻击策略——重装策略
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
                barracksBehavior(u, p, pgs, heavyType);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, true, 2);
    }

    // 可能以后会用的轻兵策略
    public void lightRushTactics(GameState gs, PhysicalGameState pgs, Player p,
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
                barracksBehavior(u, p, pgs, lightType);
            }
        }
        for (Unit u : myMeleeUnit) {
            if (gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        workersBehavior(myWorkers, p, gs, true, 2);
    }

    // 韬光养晦策略，全民挖矿
    public void prepareTactics(GameState gs, PhysicalGameState pgs, Player p,
                               List<Unit> myBases,
                               List<Unit> myBarracks,
                               List<Unit> myMeleeUnit,
                               List<Unit> myWorkers,
                               List<Unit> enemyBases) {
        for (Unit u : myBarracks) {
            if (gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs, heavyType);
            }
        }
        workersBehavior(myWorkers, p, gs, true, -1);
    }


    // 基地行为
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= workerType.cost) train(u, workerType);
    }

    // 兵营行为
    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs, UnitType type) {
        if (p.getResources() >= type.cost) {
            train(u, type);
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
//        // 优先攻击最近单位
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

//        if (harvestNum == -1) {
//            // 全民挖矿积累资源，同时造兵
//            while (freeWorkers.size() > 0) {
//                harvestWorker = freeWorkers.remove(0);
//                // 如果还有能挖矿的
//                if (harvestWorker != null) {
//                    harvestBehavior(harvestWorker, p, gs);
//                }
//            }
//            return;
//        } else {
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
//        }

        for (Unit u : freeWorkers) meleeUnitBehavior(u, p, gs);
    }

    // 矿工行为
    public void harvestBehavior(Unit harvestWorker, Player p, GameState gs) {
        /**
         * TODO 更改挖矿逻辑，使其不陷入死锁
         * 挖矿这里有bug，猜测是底层的问题。一旦资源不可达，则单位会陷入盲等，
         * 且wait时间不断增加。目前已经尝试过：
         * 由于没办法直接知道目标位置是否可达，导致了一系列问题。这里采用判断
         * 当前状态是否为广义死锁（广义死锁：所有我方单位均为无任务或者有等待任务
         * 状态；狭义死锁：所有我方单位均为有任务且等待任务状态），如果不是则正常
         * 运行，否则执行以下操作：
         * 首先将基地周围的单位作为当前矿工（猜测围住基地导致基地不可达，从而也会导致死锁），
         * 改变当前矿工的采矿目标，全局维护一个HashMap<Long,ArrayList<Long>>，
         * HashMap的key存储当前单位的ID，后面的ArrayList存储当前单位不可达的
         * 资源列表。在每一次harvestBehavior中，首先对资源进行排序，将排序结果存储
         * 在resourceList中，排序后从前往后遍历resourceList，如果当前资源在HashMap
         * 中当前单位对应的ArrayList中，则代表该资源不可达，继续往后遍历，直到找到
         * 可达的最近资源。
         * 尝试解除死锁的失败尝试：
         * 1. 让围在基地周边的单位去挖矿。矿工、资源、基地位置均可达，但是调用
         *    harvest()后依然陷入盲等
         * 2. 让围在基地周边的单位move，经过尝试，如果目标位置无单位，则可以
         *    暂时解除死锁，如果目标处有单位或不可达，则会继续陷入死锁，并且
         *    后续wai时间也会不断增大，目前没有找到接口能够强行移除单位身上的
         *    action
         * 目前先搁置这个问题，直接让lock标志永远为false
         */
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestBase = null;
        Unit closestResource = null;
        int closestDistance = 0;
        boolean lock = true;


        // 判断是否死锁（广义死锁）
        List<Unit> unitList = pgs.getUnits();
        for (int i = 0; i < unitList.size(); i++) {
            Unit u = unitList.get(i);
            if (u.getPlayer() == p.getID() && u.getType() == workerType) {
                UnitActionAssignment uaa = gs.getActionAssignment(u);
                if (uaa != null && uaa.action.getType() != UnitAction.TYPE_NONE) {
                    lock = false;
                }
                if (!lock) {
                    preResource.put(harvestWorker.getID(), new ArrayList<>());
                    break;
                }
            }
        }

        lock = false;

        // 如果死锁
        if (lock) {
            List<Unit> lockWorkerList = getAroundBaseWorkers(p, gs);
            if (lockWorkerList != null && lockWorkerList.size() != 0) {
                harvestWorker = lockWorkerList.get((int) (Math.random() * (lockWorkerList.size() - 1)));
            }
        }

        // 资源列表
        List<Unit> resourceList = new ArrayList<>();
        List<Integer> distanceList = new ArrayList<>();
        for (Unit u1 : pgs.getUnits()) {
            if (u1.getType().isResource) {
                int d = Math.abs(u1.getX() - harvestWorker.getX()) + Math.abs(u1.getY() - harvestWorker.getY());
                resourceList.add(u1);
                distanceList.add(d);
            }
        }

        // 资源距离排序
        for (int i = 0; i < resourceList.size(); i++) {
            for (int j = i; j < resourceList.size(); j++) {
                if (distanceList.get(i) > distanceList.get(j)) {
                    Integer tmpInt = distanceList.get(i);
                    distanceList.set(i, distanceList.get(j));
                    distanceList.set(j, tmpInt);
                    Unit tmpUnit = resourceList.get(i);
                    resourceList.set(i, resourceList.get(j));
                    resourceList.set(j, tmpUnit);
                }
            }
        }

        // 想办法去最近的资源处挖矿
        for (int i = 0; i < resourceList.size(); i++) {
            Unit u1 = resourceList.get(i);
            if (!lock || preResource.get(harvestWorker.getID()) == null
                    || preResource.get(harvestWorker.getID()) != null && !preResource.get(harvestWorker.getID()).contains(u1.getID())) {
                closestResource = u1;
                break;
            }
        }
//        System.out.println(preResource.get(harvestWorker.getID()));
//        System.out.println(harvestWorker);
//        System.out.println(closestResource);
//        System.out.println(resourceList);
//        System.out.println(distanceList);

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


        // TODO 这里需要改
        if (closestResource != null && closestBase != null) {
            AbstractAction aa = getAbstractAction(harvestWorker);
            if (aa instanceof Harvest) {
                Harvest h_aa = (Harvest) aa;
                if (h_aa.target != closestResource || h_aa.base != closestBase) {
                    harvest(harvestWorker, closestResource, closestBase);
                    if (!preResource.get(harvestWorker.getID()).contains(closestResource.getID())) {
                        preResource.get(harvestWorker.getID()).add(closestResource.getID());
                    }
                }
            } else {
                if (lock) {
//                    int x = (int) (Math.random() * (pgs.getWidth() - 1));
//                    int y = (int) (Math.random() * (pgs.getHeight() - 1));
//                    while (true) {
//                        boolean flag = true;
//                        for (Unit u2 : pgs.getUnits()) {
//                            if (u2.getX() == x && u2.getY() == y && u2.getID() != harvestWorker.getID()) {
//                                flag = false;
//                            }
//                        }
//                        if (!flag) {
//                            x = (int) (Math.random() * (15 - 1));
//                            y = (int) (Math.random() * (15 - 1));
//                        } else {
//                            break;
//                        }
//                    }
//                    move(harvestWorker, x, y);
//                    System.out.println(x + "   " + y);
//                    System.out.println(gs.getActionAssignment(harvestWorker));
//                    System.out.println();
                    return;
                }
                harvest(harvestWorker, closestResource, closestBase);
            }
        } else if (closestResource == null) {
            // 前面已经判断过能否建造基地了，所以此时必然是没有资源同时没有基地的窘境
            // 进退维谷，四面楚歌，此时不杀，更待何时？！
            // 杀！
            meleeUnitBehavior(harvestWorker, p, gs);
        }
    }


    // 工具类——实验证明死锁根本原因在于挖矿函数
    // 返回基地周边的worker
    public List<Unit> getAroundBaseWorkers(Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        ArrayList<Unit> aroundBaseWorkers = new ArrayList<>();
        Unit base = null;
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType &&
                    u.getPlayer() == p.getID()) {
                base = u;
                for (Unit u1 : pgs.getUnits()) {
                    if (u1.getPlayer() == p.getID() && base != null) {
                        if (u1.getType() == workerType && 1 == Math.abs(u1.getX() - base.getX()) + Math.abs(u1.getY() - base.getY())) {
                            aroundBaseWorkers.add(u1);
                        }
                    }
                }
            }
        }

        return aroundBaseWorkers;
    }

    // 判断是否死锁（狭义死锁）
    public boolean isLock(Player p, GameState gs) {
        System.out.println("lock");
        PhysicalGameState pgs = gs.getPhysicalGameState();
        boolean lock = true;
        // 判断是否死锁
        List<Unit> unitList = pgs.getUnits();
        for (int i = 0; i < unitList.size(); i++) {
            Unit u = unitList.get(i);
            if (u.getPlayer() == p.getID()) {
                UnitActionAssignment uaa = gs.getActionAssignment(u);
                if (uaa == null) lock = false;
                if (uaa != null && uaa.action.getType() != UnitAction.TYPE_NONE) {
                    lock = false;
                }
                if (!lock) {
                    break;
                }
            }
        }
        return lock;
    }


    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }
}
