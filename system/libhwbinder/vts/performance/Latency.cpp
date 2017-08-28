/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/hardware/tests/libhwbinder/1.0/IScheduleTest.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <tuple>
#include <vector>

#include <pthread.h>
#include <sys/wait.h>
#include <unistd.h>

using namespace std;
using namespace android;
using namespace android::hardware;

using android::hardware::tests::libhwbinder::V1_0::IScheduleTest;

#define ASSERT(cond)                                                \
  do {                                                              \
    if (!(cond)) {                                                  \
      cerr << __func__ << ":" << __LINE__ << " condition:" << #cond \
           << " failed\n"                                           \
           << endl;                                                 \
      exit(EXIT_FAILURE);                                           \
    }                                                               \
  } while (0)

vector<sp<IScheduleTest> > services;

// the ratio that the service is synced on the same cpu beyond
// GOOD_SYNC_MIN is considered as good
#define GOOD_SYNC_MIN (0.6)

#define DUMP_PRICISION 2

string trace_path = "/sys/kernel/debug/tracing";

// the default value
int no_pair = 1;
int iterations = 100;
int no_inherent = 0;
int no_sync = 0;
int verbose = 0;
int trace;
bool pass_through = false;

static bool traceIsOn() {
  fstream file;
  file.open(trace_path + "/tracing_on", ios::in);
  char on;
  file >> on;
  file.close();
  return on == '1';
}

static void traceStop() {
  ofstream file;
  file.open(trace_path + "/tracing_on", ios::out | ios::trunc);
  file << '0' << endl;
  file.close();
}

// the deadline latency that we are interested in
uint64_t deadline_us = 2500;

static int threadPri() {
  struct sched_param param;
  int policy;
  ASSERT(!pthread_getschedparam(pthread_self(), &policy, &param));
  return param.sched_priority;
}

static void threadDump(const char* prefix) {
  struct sched_param param;
  int policy;
  if (!verbose) return;
  cout << "--------------------------------------------------" << endl;
  cout << setw(12) << left << prefix << " pid: " << getpid()
       << " tid: " << gettid() << " cpu: " << sched_getcpu() << endl;
  ASSERT(!pthread_getschedparam(pthread_self(), &policy, &param));
  string s = (policy == SCHED_OTHER)
                 ? "SCHED_OTHER"
                 : (policy == SCHED_FIFO)
                       ? "SCHED_FIFO"
                       : (policy == SCHED_RR) ? "SCHED_RR" : "???";
  cout << setw(12) << left << s << param.sched_priority << endl;
  return;
}

// This IPC class is widely used in binder/hwbinder tests.
// The common usage is the main process to create the Pipe and forks.
// Both parent and child hold a object and each wait() on parent
// needs a signal() on the child to wake up and vice versa.
class Pipe {
  int m_readFd;
  int m_writeFd;
  Pipe(int readFd, int writeFd) : m_readFd{readFd}, m_writeFd{writeFd} {}
  Pipe(const Pipe&) = delete;
  Pipe& operator=(const Pipe&) = delete;
  Pipe& operator=(const Pipe&&) = delete;

 public:
  Pipe(Pipe&& rval) noexcept {
    m_readFd = rval.m_readFd;
    m_writeFd = rval.m_writeFd;
    rval.m_readFd = 0;
    rval.m_writeFd = 0;
  }
  ~Pipe() {
    if (m_readFd) close(m_readFd);
    if (m_writeFd) close(m_writeFd);
  }
  void signal() {
    bool val = true;
    int error = write(m_writeFd, &val, sizeof(val));
    ASSERT(error >= 0);
  };
  void wait() {
    bool val = false;
    int error = read(m_readFd, &val, sizeof(val));
    ASSERT(error >= 0);
  }
  template <typename T>
  void send(const T& v) {
    int error = write(m_writeFd, &v, sizeof(T));
    ASSERT(error >= 0);
  }
  template <typename T>
  void recv(T& v) {
    int error = read(m_readFd, &v, sizeof(T));
    ASSERT(error >= 0);
  }
  static tuple<Pipe, Pipe> createPipePair() {
    int a[2];
    int b[2];

    int error1 = pipe(a);
    int error2 = pipe(b);
    ASSERT(error1 >= 0);
    ASSERT(error2 >= 0);

    return make_tuple(Pipe(a[0], b[1]), Pipe(b[0], a[1]));
  }
};

typedef chrono::time_point<chrono::high_resolution_clock> Tick;

static inline Tick tickNow() { return chrono::high_resolution_clock::now(); }

static inline uint64_t tickNano(Tick& sta, Tick& end) {
  return uint64_t(
      chrono::duration_cast<chrono::nanoseconds>(end - sta).count());
}

struct Results {
  uint64_t m_best = 0xffffffffffffffffULL;
  uint64_t m_worst = 0;
  uint64_t m_transactions = 0;
  uint64_t m_total_time = 0;
  uint64_t m_miss = 0;
  bool tracing;
  Results(bool _tracing) : tracing(_tracing) {}
  inline bool miss_deadline(uint64_t nano) { return nano > deadline_us * 1000; }
  void add_time(uint64_t nano) {
    m_best = min(nano, m_best);
    m_worst = max(nano, m_worst);
    m_transactions += 1;
    m_total_time += nano;
    if (miss_deadline(nano)) m_miss++;
    if (miss_deadline(nano) && tracing) {
      // There might be multiple process pair running the test concurrently
      // each may execute following statements and only the first one actually
      // stop the trace and any traceStop() afterthen has no effect.
      traceStop();
      cout << endl;
      cout << "deadline triggered: halt & stop trace" << endl;
      cout << "log:" + trace_path + "/trace" << endl;
      cout << endl;
      exit(1);
    }
  }
  void dump() {
    double best = (double)m_best / 1.0E6;
    double worst = (double)m_worst / 1.0E6;
    double average = (double)m_total_time / m_transactions / 1.0E6;
    int W = DUMP_PRICISION + 2;
    cout << std::setprecision(DUMP_PRICISION) << "{ \"avg\":" << setw(W) << left
         << average << ", \"wst\":" << setw(W) << left << worst
         << ", \"bst\":" << setw(W) << left << best << ", \"miss\":" << left
         << m_miss << ", \"meetR\":" << setprecision(DUMP_PRICISION + 3) << left
         << (1.0 - (double)m_miss / m_transactions) << "}";
  }
};

static string generateServiceName(int num) {
  string serviceName = "hwbinderService" + to_string(num);
  return serviceName;
}

// This private struct is used to pass the argument to threadStart
// result: a pointer to Results
// target: the terget service number
typedef struct {
  void* result;
  int target;
} thread_priv_t;

static void* threadStart(void* p) {
  thread_priv_t* priv = (thread_priv_t*)p;
  int target = priv->target;
  Results* results_fifo = (Results*)priv->result;
  Tick sta, end;

  threadDump("fifo-caller");
  uint32_t call_sta = (threadPri() << 16) | sched_getcpu();
  sp<IScheduleTest> service = services[target];
  sta = tickNow();
  uint32_t ret = service->send(verbose, call_sta);
  end = tickNow();
  results_fifo->add_time(tickNano(sta, end));

  no_inherent += (ret >> 16) & 0xffff;
  no_sync += ret & 0xffff;
  return 0;
}

// create a fifo thread to transact and wait it to finished
static void threadTransaction(int target, Results* results_fifo) {
  thread_priv_t thread_priv;

  void* dummy;
  pthread_t thread;
  pthread_attr_t attr;
  struct sched_param param;
  thread_priv.target = target;
  thread_priv.result = results_fifo;
  ASSERT(!pthread_attr_init(&attr));
  ASSERT(!pthread_attr_setschedpolicy(&attr, SCHED_FIFO));
  param.sched_priority = sched_get_priority_max(SCHED_FIFO);
  ASSERT(!pthread_attr_setschedparam(&attr, &param));
  ASSERT(!pthread_create(&thread, &attr, threadStart, &thread_priv));
  ASSERT(!pthread_join(thread, &dummy));
}

static void serviceFx(const string& serviceName, Pipe p) {
  // Start service.
  sp<IScheduleTest> server = IScheduleTest::getService(serviceName, true);
  status_t status = server->registerAsService(serviceName);
  if (status != ::android::OK) {
    cout << "Failed to register service " << serviceName.c_str() << endl;
    exit(EXIT_FAILURE);
  }
  // tell main I'm init-ed
  p.signal();
  // wait for kill
  p.wait();
  exit(EXIT_SUCCESS);
}

static Pipe makeServiceProces(string service_name) {
  auto pipe_pair = Pipe::createPipePair();
  pid_t pid = fork();
  if (pid) {
    // parent
    return move(get<0>(pipe_pair));
  } else {
    threadDump("service");
    // child
    serviceFx(service_name, move(get<1>(pipe_pair)));
    // never get here
    ASSERT(0);
    return move(get<0>(pipe_pair));
  }
}

static void clientFx(int num, int server_count, int iterations, Pipe p) {
  Results results_other(false), results_fifo(trace);

  for (int i = 0; i < server_count; i++) {
    sp<IScheduleTest> service =
        IScheduleTest::getService(generateServiceName(i), pass_through);
    ASSERT(service != nullptr);
    if (pass_through) {
      ASSERT(!service->isRemote());
    } else {
      ASSERT(service->isRemote());
    }
    services.push_back(service);
  }
  // tell main I'm init-ed
  p.signal();
  // wait for kick-off
  p.wait();

  // Client for each pair iterates here
  // each iterations contains exatcly 2 transactions
  for (int i = 0; i < iterations; i++) {
    Tick sta, end;
    // the target is paired to make it easier to diagnose
    int target = num;

    // 1. transaction by fifo thread
    threadTransaction(target, &results_fifo);
    threadDump("other-caller");

    uint32_t call_sta = (threadPri() << 16) | sched_getcpu();
    sp<IScheduleTest> service = services[target];
    // 2. transaction by other thread
    sta = tickNow();
    uint32_t ret = service->send(verbose, call_sta);
    end = tickNow();
    results_other.add_time(tickNano(sta, end));
    no_inherent += (ret >> 16) & 0xffff;
    no_sync += ret & 0xffff;
  }
  // tell main i'm done
  p.signal();
  // wait for kill
  p.wait();
  // Client for each pair dump here
  int no_trans = iterations * 2;
  double sync_ratio = (1.0 - (double)no_sync / no_trans);
  cout << "\"P" << num << "\":{\"SYNC\":\""
       << ((sync_ratio > GOOD_SYNC_MIN) ? "GOOD" : "POOR") << "\","
       << "\"S\":" << (no_trans - no_sync) << ",\"I\":" << no_trans << ","
       << "\"R\":" << sync_ratio << "," << endl;

  cout << "  \"other_ms\":";
  results_other.dump();
  cout << "," << endl;
  cout << "  \"fifo_ms\": ";
  results_fifo.dump();
  cout << endl;
  cout << "}," << endl;
  exit(no_inherent);
}

static Pipe makeClientProcess(int num, int iterations, int no_pair) {
  auto pipe_pair = Pipe::createPipePair();
  pid_t pid = fork();
  if (pid) {
    // parent
    return move(get<0>(pipe_pair));
  } else {
    // child
    threadDump("client");
    clientFx(num, no_pair, iterations, move(get<1>(pipe_pair)));
    // never get here
    ASSERT(0);
    return move(get<0>(pipe_pair));
  }
}

static void waitAll(vector<Pipe>& v) {
  for (size_t i = 0; i < v.size(); i++) {
    v[i].wait();
  }
}

static void signalAll(vector<Pipe>& v) {
  for (size_t i = 0; i < v.size(); i++) {
    v[i].signal();
  }
}

// This test is modified from frameworks/native/libs/binder/tests/sch-dbg.cpp
// The difference is sch-dbg tests binder transaction and this one test
// HwBinder transaction.
int main(int argc, char** argv) {
  setenv("TREBLE_TESTING_OVERRIDE", "true", true);

  vector<Pipe> client_pipes;
  vector<Pipe> service_pipes;

  for (int i = 1; i < argc; i++) {
    if (string(argv[i]) == "-passthrough") {
      pass_through = true;
    }
    if (string(argv[i]) == "-i") {
      iterations = atoi(argv[i + 1]);
      i++;
      continue;
    }
    if (string(argv[i]) == "-pair") {
      no_pair = atoi(argv[i + 1]);
      i++;
      continue;
    }
    if (string(argv[i]) == "-deadline_us") {
      deadline_us = atoi(argv[i + 1]);
      i++;
      continue;
    }
    if (string(argv[i]) == "-v") {
      verbose = 1;
    }
    // The -trace argument is used like that:
    //
    // First start trace with atrace command as usual
    // >atrace --async_start sched freq
    //
    // then use the -trace arguments like
    // -trace -deadline_us 2500
    //
    // This makes the program to stop trace once it detects a transaction
    // duration over the deadline. By writing '0' to
    // /sys/kernel/debug/tracing and halt the process. The tracelog is
    // then available on /sys/kernel/debug/trace
    if (string(argv[i]) == "-trace") {
      trace = 1;
    }
  }
  if (!pass_through) {
    // Create services.
    for (int i = 0; i < no_pair; i++) {
      string serviceName = generateServiceName(i);
      service_pipes.push_back(makeServiceProces(serviceName));
    }
    // Wait until all services are up.
    waitAll(service_pipes);
  }
  if (trace && !traceIsOn()) {
    cout << "trace is not running" << endl;
    cout << "check " << trace_path + "/tracing_on" << endl;
    cout << "use atrace --async_start first" << endl;
    exit(-1);
  }
  threadDump("main");
  cout << "{" << endl;
  cout << "\"cfg\":{\"pair\":" << (no_pair) << ",\"iterations\":" << iterations
       << ",\"deadline_us\":" << deadline_us << "}," << endl;

  // the main process fork 2 processes for each pairs
  // 1 server + 1 client
  // each has a pipe to communicate with
  for (int i = 0; i < no_pair; i++) {
    client_pipes.push_back(makeClientProcess(i, iterations, no_pair));
  }
  // wait client to init
  waitAll(client_pipes);

  // kick off clients
  signalAll(client_pipes);

  // wait client to finished
  waitAll(client_pipes);

  if (!pass_through) {
    // Kill all the services.
    for (int i = 0; i < no_pair; i++) {
      int status;
      service_pipes[i].signal();
      wait(&status);
      if (status != 0) {
        cout << "nonzero child status" << status << endl;
      }
    }
  }
  for (int i = 0; i < no_pair; i++) {
    int status;
    client_pipes[i].signal();
    wait(&status);
    // the exit status is number of transactions without priority inheritance
    // detected in the child process
    no_inherent += status;
  }
  cout << "\"inheritance\": " << (no_inherent == 0 ? "\"PASS\"" : "\"FAIL\"")
       << endl;
  cout << "}" << endl;
  return -no_inherent;
}
