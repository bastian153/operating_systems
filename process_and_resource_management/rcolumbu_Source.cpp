#include <iostream>
#include <string>
#include <sstream>
#include <algorithm>
#include <iterator>
#include <vector>
#include <utility>
#include <list>
#include <map>
#include <fstream>

using namespace std;

class PCB;
class RCB;
vector<RCB*> rcbList;
vector<list<PCB*>> readyList(3);
PCB* highestProcess;
bool releaseResourcesFlag = false;
bool DelProcessInWaitlistFlag = false;
ofstream writer("31652011.txt");

class RCB
{
	string rid;
	int units;
	int unitsAvailable;
	list<PCB*> waitList;

public:

	RCB() {}
	RCB(string& rid, int units, int unitsAvailable)
	{
		this->rid = rid;
		this->units = units;
		this->unitsAvailable = unitsAvailable;

	}
	string getRid() { return rid; }
	int getUnits() { return units; }
	int getUnitsAvailable() { return unitsAvailable; }
	void setUnitsAvailable(int unitsLeft) { this->unitsAvailable = unitsLeft; }
	void addToWaitList(PCB* process)
	{
		this->waitList.push_back(process);
	}
	void removeFrontFromWaitList()
	{
		this->waitList.pop_front();
	}
	list<PCB*> getWaitList() { return waitList; }
};

class PCB
{
	string ppid;
	string pid;
	list <RCB*> other_resources; // each of these nodes will be ponting to some resource that has been requested from before
	string status; // running, ready or blocked

	int priority;
	int resourcesRequested;

public:
	static map<string, list<PCB*>> creationTree;
	PCB() {}
	PCB(string init) {
		this->pid = "init";
		this->status = "Ready";
		this->priority = 0;
		this->creationTree.insert(pair <string, list<PCB*>>(init, list<PCB*>()));
	}

	void createProcess(string pid, int priority);
	void destroyProcess(string pid);
	void scheduler();
	void killTree(list<PCB*>& tree);
	void requestResource(string rid, int unitsRequested);
	void removeFromReadyList();
	void freeResources();
	void releaseResource(string rid, int unitsReleased);
	void freeResourcesWithRid(string rid, int unitsReleased);
	void timeOut();
	void deleteHelper();
	void reboot();
	PCB* findInResourceWaitList(string pid);
};

map<string, list<PCB*>> PCB::creationTree;

int checkCommand(string command)
{
	if (command == "cr") // create
		return 0;
	else if (command == "de") // destroy
		return 1;
	else if (command == "req") // request
		return 2;
	else if (command == "rel") // release
		return 3;
	else if (command == "to") // time-out
		return 4;
	else if (command == "init")
		return 5;
	return -1;
}

void createRCBList()
{
	for (int i = 0; i < 4; ++i)
	{
		string resourceName = "R" + to_string(i + 1);
		RCB* rcbListItem = new RCB(resourceName, i + 1, i + 1);
		rcbList.push_back(rcbListItem);
	}
}

void PCB::reboot()
{
	creationTree.clear();
	rcbList.clear();
	createRCBList();
	readyList[0].front()->creationTree["init"] = list<PCB*>();
	readyList[1].clear();
	readyList[2].clear();

	scheduler();
}

void PCB::scheduler()
{
	highestProcess = NULL;
	list<PCB*>::iterator it;

	for (int i = 2; i >= 0; --i)
	{
		if (readyList[i].size() != 0)
		{
			it = readyList[i].begin();
			highestProcess = *it;
			break;
		}
	}

	if (this->priority < highestProcess->priority || this->status != "Running" || this == NULL)
		highestProcess->status = "Running";
	writer << highestProcess->pid << " ";
}

void PCB::timeOut()
{
	readyList[this->priority].pop_front();
	this->status = "Ready";
	readyList[this->priority].push_back(this);
	scheduler();
}

void PCB::freeResourcesWithRid(string rid, int unitsReleased)
{
	list<RCB*>::iterator it = this->other_resources.begin();

	for (RCB* block : this->other_resources)
	{
		if (rid == block->getRid())
		{
			if (block->getUnitsAvailable() == unitsReleased)
			{
				int index = block->getRid().back() - '0' - 1;
				rcbList[index]->setUnitsAvailable(rcbList[index]->getUnitsAvailable() + block->getUnitsAvailable());
				if (releaseResourcesFlag == false)
					this->other_resources.erase(it);
				break;
			}
			else if (block->getUnitsAvailable() > unitsReleased)
			{
				int index = block->getRid().back() - '0' - 1;
				rcbList[index]->setUnitsAvailable(rcbList[index]->getUnitsAvailable() + block->getUnitsAvailable());
				block->setUnitsAvailable(block->getUnitsAvailable() - unitsReleased);
				break;
			}
			else
				writer << "error ";
		}
		++it;
	}
}

void PCB::releaseResource(string rid, int unitsReleased)
{
	char lastC = rid.back();
	RCB* r = rcbList[(lastC - '0') - 1];
	int deleteCounter = 0;
    
    if ((lastC - '0') > 4) {
        writer << "error ";
        return;
    }

	freeResourcesWithRid(rid, unitsReleased);
	list<PCB*> waitList = r->getWaitList();

	for (PCB* waitListBlock : waitList)
	{
		if (waitListBlock->resourcesRequested > r->getUnitsAvailable())
			break;
		else {
			waitListBlock->status = "Ready";
			RCB* block = new RCB(rid, 5, waitListBlock->resourcesRequested);
			r->setUnitsAvailable(r->getUnitsAvailable() - waitListBlock->resourcesRequested);
			waitListBlock->other_resources.push_back(block);
			waitListBlock->resourcesRequested = 0;
			readyList[waitListBlock->priority].push_back(waitListBlock);
			++deleteCounter;
		}
	}

	for (int i = 0; i < deleteCounter; ++i)
		r->removeFrontFromWaitList();

	if (releaseResourcesFlag == false)
		scheduler();


}

void PCB::removeFromReadyList()
{
	for (int i = 2; i > 0; --i)
	{
		if (readyList[i].size() != 0)
		{
			if (readyList[i].front()->pid == this->pid)
			{
				readyList[i].pop_front();
				break;
			}
		}
	}
}

void PCB::requestResource(string rid, int unitsRequested)
{
	char lastC = rid.back();
	RCB* r = rcbList[(lastC - '0') - 1];
	list<RCB*>::iterator it;
	int schdlFlag = 1;
    
    if ((lastC - '0') > 4) {
        writer << "error ";
        return;
    }

	if (r->getUnits() < unitsRequested)
	{
		writer << "error ";
		return;
	}

	if (r->getUnitsAvailable() >= unitsRequested)
	{
		r->setUnitsAvailable(r->getUnitsAvailable() - unitsRequested);
		for (it = this->other_resources.begin(); it != this->other_resources.end(); ++it)
		{
			if ((*it)->getRid() == rid)
			{
				(*it)->setUnitsAvailable((*it)->getUnitsAvailable() + unitsRequested);
				break;
			}
		}
		if (it == this->other_resources.end())
		{
			RCB* block = new RCB(rid, 5, unitsRequested);
			this->other_resources.push_back(block);
		}
	}
	else
	{
		bool completeness = true;
		for (RCB* resource : this->other_resources)
			if (resource->getRid() == rid)
				completeness = false;
		
		if (completeness == true)
		{
			this->status = "Blocked";
			this->removeFromReadyList();
			this->resourcesRequested = unitsRequested;
			r->addToWaitList(this);
		}
		else {
			writer << "error ";
			schdlFlag = 0;
		}
	}

	if (schdlFlag == 1)
		scheduler();
}

void PCB::freeResources()
{
	for (RCB* block : this->other_resources)
		this->releaseResource(block->getRid(), block->getUnitsAvailable());

	this->other_resources.clear();
}

void PCB::deleteHelper()
{
	list<PCB*>::iterator it;

	for (it = readyList[this->priority].begin(); it != readyList[this->priority].end(); ++it)
	{
		if (this->pid == (*it)->pid)
		{
			readyList[this->priority].erase(it);
			break;
		}
	}
}

void PCB::killTree(list<PCB*>& tree)
{
	PCB* tempP = NULL;
	for (list<PCB*>::iterator it1 = tree.begin(); it1 != tree.end(); )
	{
		list<PCB*>& tempTree = (*it1)->creationTree[(*it1)->pid];
		killTree(tempTree);
		releaseResourcesFlag = true;
		freeResources();
		(*it1)->deleteHelper();

		list<PCB*>::iterator tempP = it1++;
		string tempPid = (*tempP)->pid;
		if (creationTree[(*tempP)->ppid].size() > 0)
			creationTree[(*tempP)->ppid].pop_front();
		creationTree.erase(tempPid);
	}
}

PCB* PCB::findInResourceWaitList(string pid)
{
	for (RCB* link : rcbList)
	{
		if (link->getWaitList().size() != 0)
		{
			for (list<PCB*>::iterator it1 = link->getWaitList().begin(); it1 != link->getWaitList().end(); it1++)
			{
				if ((*it1)->pid == pid)
				{
					return (*it1);
				}
			}
		}
	}

	return NULL;
}

void PCB::destroyProcess(string pid) // might need to delete the root process outside of the function
{
	PCB* startPointer = NULL;

	if (pid == "init") {
		writer << "error ";
		return;
	}

	if (this->pid == pid)
		startPointer = this;
	else
	{
		for (PCB* pointer : this->creationTree[this->pid])
		{
			if (pointer->pid == pid)
			{
				startPointer = pointer;
				break;
			}
		}
		if (startPointer == NULL) {
			startPointer = findInResourceWaitList(pid);
			if (startPointer == NULL) {
				writer << "error ";
				return;
			}
			DelProcessInWaitlistFlag = true;
		}
			
	}

	if (startPointer != NULL)
	{
		list<PCB*>& tree = this->creationTree[pid];
		killTree(tree);
		releaseResourcesFlag = true;
		if (DelProcessInWaitlistFlag != true)
		{
			startPointer->freeResources();
			startPointer->deleteHelper();
		}
		
		for (list<PCB*>::iterator it = creationTree[startPointer->ppid].begin(); it != creationTree[startPointer->ppid].end(); ++it)
		{
			if ((*it)->pid == startPointer->pid)
			{
				creationTree[startPointer->ppid].erase(it);
				break;
			}
		}
		creationTree.erase(startPointer->pid);

		delete startPointer;
	}
	else
		writer << "error ";
	DelProcessInWaitlistFlag = false;
	scheduler();
}

void PCB::createProcess(string pid, int priority)
{
	if (this->creationTree.find(pid) != this->creationTree.end() || priority > 2 || priority <= 0)
	{
		writer << "error ";
		return;
	}

	PCB* newProcess = new PCB();
	newProcess->ppid = this->pid;
	newProcess->pid = pid;
	newProcess->status = "Ready";
	newProcess->priority = priority;

	newProcess->creationTree.insert(pair<string, list<PCB*>>(pid, list<PCB*>())); // within the map from new process we create a node with node(pid) having no children
	this->creationTree[this->pid].push_back(newProcess);// we add the new process to the map from the process that called this function

	readyList[priority].push_back(newProcess);

	scheduler();
}

void runCommand(PCB* currentProcess, int commandNumber, vector<string> &tokens)
{
	switch (commandNumber)
	{
	case 0: currentProcess->createProcess(tokens[1], stoi(tokens[2]));
		break;
	case 1: currentProcess->destroyProcess(tokens[1]);
		break;
	case 2: currentProcess->requestResource(tokens[1], stoi(tokens[2]));
		break;
	case 3: releaseResourcesFlag = false;
		currentProcess->releaseResource(tokens[1], stoi(tokens[2]));
		break;
	case 4: currentProcess->timeOut();
		break;
	case 5: currentProcess->reboot();

	}
}

int main(void)
{
	string command;
	int commandNumber = 0;
	bool initFlag = true;
	ifstream reader("input.txt", ifstream::in);

    //cout<< command << endl;

	if (!reader.is_open())
		exit(101);

	PCB* currentProcess = new PCB("init");
	readyList[0].push_back(currentProcess);
	currentProcess->scheduler();

	createRCBList();

	while (getline(reader, command))
	{
        //cout<< command << endl;
		istringstream iss(command);

		vector<string> tokens{ istream_iterator<string>{iss}, istream_iterator<string>{} };
		if (tokens.size() != 0)
		{
			commandNumber = checkCommand(tokens[0]);
			if (commandNumber != -1)
			{
				runCommand(currentProcess, commandNumber, tokens);
				currentProcess = highestProcess;
			}
			else {
				writer << command;
			}
		}
		else {
			writer << "\n";
		}
	}

	return 0;
}
