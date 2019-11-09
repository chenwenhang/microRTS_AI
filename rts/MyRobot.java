package ai.abstraction;

/**
 * @Author: Wenhang Chen
 * @Description:
 * @Date: Created in 16:11 10/22/2019
 * @Modified by:
 */

import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;

public class MyRobot extends AbstractionLayerAI {
    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType heavyType;


    // Strategy implemented by this class:
    // If we have more than 1 "Worker": send the extra workers to attack to the nearest enemy unit
    // If we have a base: train workers non-stop
    // If we have a worker: do this if needed: build base, harvest resources
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
        }
    }


    public AI clone() {
        return new MyRobot(utt, pf);
    }

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
//        System.out.println("LightRushAI for player " + player + " (cycle " + gs.getTime() + ")");

        // 基地行为
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType &&
                    u.getPlayer() == player &&
                    gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }

        // 兵营行为
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                barracksBehavior(u, p, pgs);
            }
        }

        // 攻击单位行为
        // 可攻击但不可采集资源
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest &&
                    u.getPlayer() == player &&
                    gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }

        // behavior of workers:
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest &&
                    u.getPlayer() == player) {
                workers.add(u);
            }
        }
        workersBehavior(workers, p, gs);


        return translateActions(player, gs);
    }

    // 基地
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        // 训练
        if (p.getResources() >= workerType.cost) train(u, workerType);
    }

    // 兵营
    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= heavyType.cost) {
            train(u, heavyType);
        }
    }

    // 攻击
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

    public void workersBehavior(List<Unit> workers, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int nbases = 0;
        int resourcesUsed = 0;
        Unit harvestWorker = null;
        List<Unit> freeWorkers = new LinkedList<Unit>();
        freeWorkers.addAll(workers);

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
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
                resourcesUsed += baseType.cost;
            }
        }

        if (freeWorkers.size() > 0) harvestWorker = freeWorkers.remove(0);

        // 矿工行为
        if (harvestWorker != null) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
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
                    if (h_aa.target != closestResource || h_aa.base != closestBase)
                        harvest(harvestWorker, closestResource, closestBase);
                } else {
                    harvest(harvestWorker, closestResource, closestBase);
                }
            } else if (closestResource == null) {
                // 前面已经判断过能否建造基地了，所以此时必然是没有资源同时没有基地的窘境
                // 进退维谷，四面楚歌，此时不杀，更待何时？！背水一战！！！
                meleeUnitBehavior(harvestWorker, p, gs);
            }
        }

        for (Unit u : freeWorkers) meleeUnitBehavior(u, p, gs);

    }


    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }
}
