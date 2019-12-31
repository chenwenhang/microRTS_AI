<h1 align="center">
microRTS_AI
</h1>

<div align="center">

  This repository is used to record the RTS AI algorithm for group 28.
  <br>
  Wenhang Chen, Xu Qin, Yuwen Zhou
  <br>
</div>
<br>

**Abstract**: In general, our final version of Micro-RTS is a well-tuned, multi-situation-adaption and hard-coded robot. After the competition of final tournament, our final robot ranks in the 15th of all 30 groups. Our robot gets an order of 20th in small maps while in large maps, the position of our robot is 14. Having analyzed our competition result, we conclude that hard-coded method is selectively trick which means a fixed hard-coded robot can only defeat with for certain hard-coded opponents in upper position. This protrudes the drawbacks of hard-coded robot. Other analysis such as like lock problem of workers are added following the analysis of the tournament and the RTS game.

**Keywords**: Tournament · Hard-codedStrategy · DefectofHard-coded Robot · Analysis of Micro-RTS game · Lock problem

<br>

## Introduction
In this report, we first retrospect previous reports and figure out the trace in construction of our robot. Second, we analyse the result of tournament. Third, we put out the understanding of Micro-RTS game. Besides, we put forward the reflection of locking problem in the harvesting workers.


## Review of previous report 
In the previous four reports, we put out our strategy of robot. At the first report, we introduce the basic configuration of the game. Then, we compared algorithms for different maps and found that in small maps WorkerRush outplayed and in large maps Monte Carlo Decision Tree prevails. At the second report, we analyzed the hard-coded strategy and raised the draft of different strategies like steeling home as well as luring and attacking. At the third report, we further improved previous report's draft and find the difference of diverse maps. In this situation, we designed strategies for different maps. At the fourth report, we added the strategy of grouping units together and to defend stealing home's strategy, we strengthen the worker's strategy to avoid focusing on harvesting and neglecting the enemy attacking base.

## Analysis of the tournament result 
Compared with different enemies, our robot has different problems.

### Winning Situations 
**Small Map**: For example, when competing with WorkerRush Enemy's, our robot can succeed because the effective units like Light, Heavy and Light Units can easily defeat the Worker Unit with little damage which will cumulatively store advantages by a small range. 

**Big Map**: The sequence to generate different is quite tricky. To be specific, when generating single kind of units like only Light Unit or Ranged Unit, the unit can be easily be counter with specific tactics, like use Light Unit to rush Range Unit from a different angle, or use steady Range Unit to attack Light Unit or Worker Unit. So the combination of generating Heavy Unit and  Light Unit helps us win in big maps.

### Losing Situations
**Small Map**: On one side, the strategy of generating barrack for soldiers can accumulate strength when having enough soldier units. However, when competing with stealing home tactic, the building barrack worker or the harvest worker have to drop their work to defend the staler. The defense is often late which means that the worker unit can not recognize the idea of enemy's unit. And in the attacking phase, our units assemble in a more loose array, which causes quantitative waste of units and resources, especially for competition with defended strategies.

**Big Map**: Compare with small maps, our robot is more competitive in big map, which can be illustrated by the combination of different units. However, when competing with high-order algorithm, our robot loses in most situations. By observation, our robot workers for harvesting are quietly fewer than opponent's workers for harvesting. Specifically, in the beginning, opponent unit may have few workers. But with the proceeding of the competition, opponent's base can produce more workers to harvest while our number of workers is certain. As a matter of result, the units our robot has are fewer than opponent's in the longer game time.

## Understanding of the game
RTS is a real-time-strategy game. In this game, every unit is a resource. From harvesting the resources to attacking enemy's unit to decrease opponent's resources, the tactic to win the game is to gain more resources than opponent and utilize the advantage to gain more resources. So the game can be divided into to aspects, harvesting and attacking. 

**Harvesting**: In our robot design, harvesting is rather simple by assigning certain workers to harvest the resources. However, the game can be locked in certain situation, which will be discussed in the next chapter.

**Attacking**: The hp, attacking damage and attacking range can be important aspects to influence units when they are countered in opposite directions. However, there also exists a interesting situation that the orientation of units can have a great influence on the attacking action of units. Many of the defeated situations are caused by not rotating to attack the enemy. This can be caused by the assignment of actions.
Another aspects is the combination of different units. A simple application is use worker or Light Unit to help escort Range Unit. Another is use a tactic to lure a unit and attack the unit which can bear no loss of hp. However, this kind of code is quite confusing. In other words, units in this kind of strategy need to be encoded lots of rules to adapt different situations. And the related codes are as followed:
```Java
if (closestEnemy.getType() == baseType || 
	closestEnemy.getType() == barracksType) {
    attack(u, closestEnemy);
} else if (closestDistance <= u.getAttackRange() + 2) {
    attack(u, closestEnemy);
} else if (closestDistance >= u.getAttackRange() + 4) {
    move(u, closestEnemy.getX() + baseposition * 
    	(closestEnemy.getAttackRange() + 1),
    	 closestEnemy.getY() + baseposition * 
    	 (closestEnemy.getAttackRange() + 1));
} else {
    move(u, basex - baseposition * 2, basey - 
    	baseposition * 2);
}
```

**Drawbacks of hard-coded Strategy**: There exists bug in harvesting function. We guess it's a bottom problem. Once the resources are not available, the unit will fall into blindness, And the wait time is increasing. At present, we think that the reason that there is no way to directly know whether the target location is reachable, so a series of problems are caused. Judgment about whether the current state is a generalized deadlock (generalized Deadlock: all our units have no tasks or waiting tasks is tired. We define that narrow deadlock is that all our units have tasks and wait for task status. If not, it is normal lock. 

The following method is what we think to solve the problem but we failed. First, take the units around the base as the current miners (it is speculated that surrounding the base will lead to the inaccessibility of the base, which will also lead to deadlock), Change the mining target of the current miner, maintain a HashMap < long, ArrayList < long > >. The key of the HashMap stores the ID of the current unit, and the ArrayList after it stores the ID of the current unit that is not reachable into List of resources. In each harvestbehavior, the resources are sorted first, and the sorting results are stored   In the resourcelist, after sorting, traverse the resourcelist from front to back. If the current resource is in the HashMap and the ArrayList corresponding to the current unit in represents that the resource is not reachable, continue to traverse until it is found   The closest resources available.

Failed attemption to unlock: 

1. Let the units around the base dig. Miner, resource and base are all accessible, but call   Still fall into blindness after harvest()   
2. Move the units around the base. After trying, if there are no units at the target location, temporarily release the deadlock. If the target has units or is unreachable, it will continue to fall into the deadlock.






