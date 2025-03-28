#define _GNU_SOURCE
#include<stdio.h>
#include<stdlib.h>
#include<unistd.h>
#include<malloc.h>
#include<fcntl.h>
#include<time.h>
#include<pthread.h>
#include<string.h>
#include<errno.h>
#include<sys/queue.h>
#include<stdatomic.h> 
#include<ctype.h>
#include <stdbool.h> 
#include "voidQueue.h"


#define ffprintf(fmt, args...) {fprintf(out_fp, fmt, ## args); fflush(out_fp); printf(fmt, ## args);}
#define assert_fopen(fp, file_name, open_type, tips, return_value) { \
    if ((fp = fopen(file_name, open_type)) == NULL) { \
        ffprintf("%s: open %s error!\n", tips, file_name); \
        return return_value; \
    }else{ \
        ffprintf("%s: open %s success!\n", tips, file_name); \
    }}
#define isdigitstr(str) (strspn(str, "0123456789")==strlen(str))

#define MINUTE 60ul
#define HOUR MINUTE*60ul
#define MAX_RUNNING_TIME HOUR*100ul

#define TRACE_N 1

bool trace_finish;
atomic_uint workload_finish;
size_t device_size;
int thread_count;
FILE *out_fp;
FILE *IOPS_fp;
time_t begin_time;
size_t MD5LenPerPage;

struct Statisic_Info {
    int IOPS[2];    //[0]Write;[1]Read
    size_t sum_IOPS[2];    //[0]Write;[1]Read
}statisic;

#define MAX_DATA_SIZE 40960
typedef struct {
    //[ts in ns] [pid] [process] [lba] [size in 512 Bytes blocks] [Write or Read] [major device number] [minor device number] [MD5 per 4096 Bytes]
	size_t time; 
    int pid;
    char process[64];
	size_t lba;
	int size;
	char ope;
    int major_device;
    int minor_device;
    unsigned char data[MAX_DATA_SIZE];
} IORequest;

struct workload_fp_info {
    FILE* workload_fp;
    long int workload_end_offset;
    int workload_id;
    size_t min_lba;
    size_t lba_num;
};

struct Print_Thread_Info {
    pthread_t thread_pid;
    char outfile_name[128];
};

struct Reading_Thread_Info {
    pthread_t thread_pid;
    char trace_path[128];
    int trace_id;
};

struct Running_Thread_Info {
    int id;
    pthread_t thread_pid;
    char device_name[128];
};

static inline unsigned long read_tsc(void) {
    unsigned long var;
    unsigned int hi, lo;

    asm volatile ("rdtsc" : "=a" (lo), "=d" (hi));
    var = ((unsigned long long int) hi << 32) | lo;

    return var;
}

// 内存池结构体
typedef struct {
    IORequest *pool;            // 内存池的指针
    bool *available;            // 标记内存池中每个元素是否可用
    int capacity;               // 内存池的大小
    pthread_mutex_t lock;       // 锁
} MemoryPool;
MemoryPool mp;

// 初始化内存池
void memory_pool_init(MemoryPool *mp, int size) {
    mp->pool = (IORequest *)malloc(size * sizeof(IORequest));
    mp->available = (bool *)malloc(size * sizeof(bool));
    mp->capacity = size;

    for (int i = 0; i < size; i++) {
        mp->available[i] = true; // 所有内存块初始时都是可用的
    }

    pthread_mutex_init(&mp->lock, NULL);
}

// 从内存池中获取一个 IORequest
IORequest *memory_pool_get(MemoryPool *mp) {
    pthread_mutex_lock(&mp->lock);

    for (int i = 0; i < mp->capacity; i++) {
        if (mp->available[i]) {
            mp->available[i] = false; // 标记为已分配
            pthread_mutex_unlock(&mp->lock);
            return &mp->pool[i];
        }
    }

    pthread_mutex_unlock(&mp->lock);
    return NULL; // 如果没有可用的内存，返回 NULL
}

// 将 IORequest 归还给内存池
void memory_pool_return(MemoryPool *mp, IORequest *request) {
    int index = (request - mp->pool); // 计算索引
    pthread_mutex_lock(&mp->lock);
    mp->available[index] = true; // 标记为可用
    pthread_mutex_unlock(&mp->lock);
}

// 释放内存池
void memory_pool_free(MemoryPool *mp) {
    free(mp->pool);
    free(mp->available);
    pthread_mutex_destroy(&mp->lock);
}

#define QUEUE_SIZE 1024
typedef struct {
    IORequest* queue[QUEUE_SIZE];
    int front;
    int rear;
    int count;
    pthread_mutex_t lock;
    pthread_cond_t not_empty;
    pthread_cond_t not_full;
} IOQueue;
IOQueue IO_queue;

// 初始化队列
void init_queue(IOQueue *q) {
    q->front = 0;
    q->rear = 0;
    q->count = 0;
    pthread_mutex_init(&q->lock, NULL);
    pthread_cond_init(&q->not_empty, NULL);
    pthread_cond_init(&q->not_full, NULL);
}

// 入队
void enqueue(IOQueue *q, IORequest* req) {
    pthread_mutex_lock(&q->lock);
    while (q->count == QUEUE_SIZE) {
        pthread_cond_wait(&q->not_full, &q->lock); // 等待空间可用
    }
    q->queue[q->rear] = req;
    q->rear = (q->rear + 1) % QUEUE_SIZE;
    q->count++;
    
    if (q->count > QUEUE_SIZE / 2) {
        pthread_cond_broadcast(&q->not_empty); // 通知所有消费者队列非空
    }
    pthread_mutex_unlock(&q->lock);
}

// 出队
IORequest* dequeue(IOQueue *q) {
    pthread_mutex_lock(&q->lock);
    while (q->count == 0) {
        pthread_cond_wait(&q->not_empty, &q->lock); // 等待数据可用
    }
    IORequest* req = q->queue[q->front];
    q->front = (q->front + 1) % QUEUE_SIZE;
    q->count--;
    pthread_cond_signal(&q->not_full); // 通知一个生产者队列非满
    pthread_mutex_unlock(&q->lock);
    return req;
}

void *workload_read(void *arg) {
    struct Reading_Thread_Info* thread_info = (struct Reading_Thread_Info*)arg;
    char trace_name[1024];
    char input_line[MAX_DATA_SIZE];

    int split_num = 21;
    voidQueue active_workload_queue;
    initQueue(&active_workload_queue, sizeof(struct workload_fp_info), split_num);
    
    if(thread_info->trace_id == 0) {
        for(int i=0; i<split_num; i++) {
            sprintf(trace_name, "%s%d%s", thread_info->trace_path, i+1, ".blkparse");

            struct workload_fp_info cur_workload;
            assert_fopen(cur_workload.workload_fp, trace_name, "r", "workload_read thread", NULL);
            fseek(cur_workload.workload_fp, 0, SEEK_END);
            cur_workload.workload_end_offset = ftell(cur_workload.workload_fp);
            cur_workload.workload_id = i;
            // cur_workload.lba_num = device_size / split_num / 8 * 8;     //4KB page align
            // cur_workload.min_lba = cur_workload.lba_num * i;
            cur_workload.lba_num = device_size / 8 * 8;     //4KB page align
            cur_workload.min_lba = 0;
            fseek(cur_workload.workload_fp, 0, SEEK_SET);

            if(enQueue(&active_workload_queue, &cur_workload) == FALSE) {
                ffprintf("%s: enQueue error!\n", "workload_read");
            }
            else {
                ffprintf("thread-%d: begin:%ld, end:%ld, lba_num:%ld, min_lba:%ld!\n", 
                        cur_workload.workload_id, ftell(cur_workload.workload_fp), cur_workload.workload_end_offset,
                        cur_workload.lba_num, cur_workload.min_lba);
            }
        }
    }
    else {
        sprintf(trace_name, "%s%d%s", thread_info->trace_path, thread_info->trace_id, ".blkparse");

        struct workload_fp_info cur_workload;
        assert_fopen(cur_workload.workload_fp, trace_name, "r", "workload_read thread", NULL);
        fseek(cur_workload.workload_fp, 0, SEEK_END);
        long int avg_workload_size = ftell(cur_workload.workload_fp) / split_num;
        fclose(cur_workload.workload_fp);

        for(int i=0; i<split_num; i++) {
            assert_fopen(cur_workload.workload_fp, trace_name, "r", "workload_read thread", NULL);
            cur_workload.workload_end_offset = avg_workload_size * (i+1);
            cur_workload.workload_id = i;
            cur_workload.lba_num = device_size / split_num / 8 * 8;     //4KB page align
            cur_workload.min_lba = cur_workload.lba_num * i;
            fseek(cur_workload.workload_fp, avg_workload_size * i, SEEK_SET);

            if(enQueue(&active_workload_queue, &cur_workload) == FALSE) {
                ffprintf("%s: enQueue error!\n", "workload_read");
            }
            else {
                ffprintf("thread-%d: begin:%ld, end:%ld, avg_size:%ld, lba_num:%ld, min_lba:%ld!\n", 
                        cur_workload.workload_id, ftell(cur_workload.workload_fp), cur_workload.workload_end_offset, avg_workload_size,
                        cur_workload.lba_num, cur_workload.min_lba);
            }
        }
    }

    struct workload_fp_info next_workload;
    next_workload.workload_fp = NULL;
    while(1) {
        IORequest* io_information  = memory_pool_get(&mp);  // 从内存池获取内存
        if (io_information == NULL) {
            usleep(10000);
            continue;
        }

        // if(delQueue(&active_workload_queue, &next_workload) == FALSE) {
        //     ffprintf("%s: delQueue error!\n", "workload_read");
        //     usleep(1000);
        //     memory_pool_return(&mp, io_information);
        //     continue;
        // }
        if(next_workload.workload_fp == NULL) {
            if (delQueue(&active_workload_queue, &next_workload) == FALSE) {
                ffprintf("%s: delQueue error!\n", "workload_read");
                usleep(1000);
                memory_pool_return(&mp, io_information);
                continue;
            }
        }

        fgets(input_line, MAX_DATA_SIZE, next_workload.workload_fp);
        int space_n = 0;
        for(char* c=input_line; (*c)!='\0'; c++)
            if((*c) == ' ') space_n++;
        if(space_n == 8) {
            sscanf(input_line, "%lu %d %s %lu %d %c %d %d %s\n", &(io_information->time), &(io_information->pid), (io_information->process), &(io_information->lba), 
                &(io_information->size), &(io_information->ope), &(io_information->major_device), &(io_information->minor_device), (io_information->data));
            
            if(io_information->lba % 8 != 0 || (io_information->ope != 'R' && io_information->ope != 'W')) {
                ffprintf("%s: lba error: %lu %d %s %lu %d %c %d %d %s\n", "workload_read thread", (io_information->time), (io_information->pid), (io_information->process), (io_information->lba), 
                        (io_information->size), (io_information->ope), (io_information->major_device), (io_information->minor_device), (io_information->data));
            } else {
                // *((int *)(io_information->data+4092)) = next_workload.workload_id;
                io_information->lba = ((io_information->lba % next_workload.lba_num + next_workload.min_lba) >> 3) << 12;     //lba (sector, 512B) -> offset (B)
                enqueue(&IO_queue, io_information); // 插入队列
            }
        }
        if(feof(next_workload.workload_fp) || ftell(next_workload.workload_fp) >= next_workload.workload_end_offset) {
            ffprintf("workload %d finish! end_fp:%ld\n", next_workload.workload_id, ftell(next_workload.workload_fp));
            // fclose(next_workload.workload_fp);
            next_workload.workload_fp = NULL;
            if(isEmptyQueue(&active_workload_queue))
                break;
        }
        // else
            // enQueue(&active_workload_queue, &next_workload);

        // if(isEmptyQueue(&active_workload_queue))
        //     break;
    }
    trace_finish = true;
    pthread_cond_broadcast(&IO_queue.not_empty); // 通知消费者队列非空
}

void *print_info_per_second(void *arg) {
    struct Print_Thread_Info* print_thread_info = (struct Print_Thread_Info*)arg;
    assert_fopen(IOPS_fp, print_thread_info->outfile_name, "w", "print_info_per_second thread", NULL);

    static int running_time = 0;
    while(workload_finish < thread_count) {
        sleep(1);
        fprintf(IOPS_fp, "%d TotalIOPS:%d ReadIOPS:%d WriteIOPS:%d SumReadIOPS:%ld(%.2fGB) SumWriteIOPS:%ld(%.2fGB), total:%.2fGB, IOQueueDeeps:%d\n", running_time, 
            statisic.IOPS[0]+statisic.IOPS[1], statisic.IOPS[1], statisic.IOPS[0], 
            statisic.sum_IOPS[1], (float)statisic.sum_IOPS[1]*4/1024/1024,
            statisic.sum_IOPS[0], (float)statisic.sum_IOPS[0]*4/1024/1024,
            (float)(statisic.sum_IOPS[0]+statisic.sum_IOPS[1])*4/1024/1024,
            IO_queue.count);
        fflush(IOPS_fp);
        printf("%d TotalIOPS:%d ReadIOPS:%d WriteIOPS:%d SumReadIOPS:%ld(%.2fGB) SumWriteIOPS:%ld(%.2fGB), total:%.2fGB, IOQueueDeeps:%d\n", running_time, 
            statisic.IOPS[0]+statisic.IOPS[1], statisic.IOPS[1], statisic.IOPS[0], 
            statisic.sum_IOPS[1], (float)statisic.sum_IOPS[1]*4/1024/1024,
            statisic.sum_IOPS[0], (float)statisic.sum_IOPS[0]*4/1024/1024,
            (float)(statisic.sum_IOPS[0]+statisic.sum_IOPS[1])*4/1024/1024,
            IO_queue.count);
        statisic.IOPS[0] = statisic.IOPS[1] = 0;
        running_time++;
    }
}

void *running_workload(void *arg) {
    struct Running_Thread_Info* running_thread_info = (struct Running_Thread_Info*)arg;

    char* write_buffer;
    posix_memalign((void **)&write_buffer, 4096, 4096);
    char* read_buffer;
    posix_memalign((void **)&read_buffer, 4096, 4096);

    int fd = -1;
    if ((fd = open(running_thread_info->device_name, O_RDWR | O_DIRECT)) == -1) {
        ffprintf("%d: open %s error!\n", running_thread_info->id, running_thread_info->device_name);
        return NULL;
    }
    else {
        ffprintf("%d: open %s success!\n", running_thread_info->id, running_thread_info->device_name);
    }

    while(1) {
        IORequest* io_information = dequeue(&IO_queue);
        
        if (trace_finish)   break;

        // // *((int *)(io_information->data+4092)) = running_thread_info->id;
        // io_information->lba = ((io_information->lba % running_thread_info->lba_num + running_thread_info->min_lba) >> 3) << 12;     //lba (sector, 512B) -> offset (B)

        if(io_information->ope == 'W') {
            for(int i=0; i<io_information->size; i+=8) {
                memcpy(write_buffer, io_information->data + MD5LenPerPage * i, MD5LenPerPage);
                int write_bytes = 0;
                write_bytes = pwrite(fd, write_buffer, 4096, (off_t)io_information->lba + ((off_t)i<<9));
                if(write_bytes == -1) {
                    ffprintf("%d: write %lu (%lu + %d*512) error!\n", running_thread_info->id, io_information->lba + (i<<9), io_information->lba, i);
                    perror("error write:");
                    // return NULL;
                }
                statisic.IOPS[0]++;
                statisic.sum_IOPS[0]++;
            }
        }
        else if(io_information->ope == 'R') {
            for(int i=0; i<io_information->size; i+=8) {
                int read_bytes = 0;
                read_bytes = pread(fd, read_buffer, 4096, (off_t)io_information->lba + ((off_t)i<<9));
                if(read_bytes == -1) {
                    ffprintf("%d: read %lu error!\n", running_thread_info->id, io_information->lba + (i<<9));
                    perror("error read:");
                    // return NULL;
                }
                statisic.IOPS[1]++;
                statisic.sum_IOPS[1]++;
            }
        }
        else {
            ffprintf("%d: error type: %c!\n", running_thread_info->id, io_information->ope);
            // return NULL;
        }
        // 处理完毕后归还内存池
        memory_pool_return(&mp, io_information);
    }
    atomic_fetch_add(&workload_finish, 1);
    close(fd);
    ffprintf("thread-%d running finish!\n", running_thread_info->id);
}

int main(int argc, char **argv) // 1[target_device] 2[total_lbas_of_device] 3[trace_dir] 4[trace_name] 5[IOPS_file] 6[thread_cnt]
{
    begin_time = time(NULL);

    out_fp = NULL;
    if ((out_fp = fopen("replay_trace.log", "w")) == NULL) {
        printf("%s: open %s error!\n", "main", "replay_trace.log");
        return 0;
    }else{
        ffprintf("%s: open %s success!\n", "main", "replay_trace.log");
    }

    if(argc!=7)
    {
        ffprintf("Error parameters!\n");
        return 0;
    }

    ffprintf("%s %s %s %s %s %s %d %d %d\n", argv[1], argv[2], argv[3], argv[4], argv[5], argv[6], 
            strcmp(argv[4], "webvm"), strcmp(argv[4], "homes"), strcmp(argv[4], "mail"));
    device_size = atoi(argv[2]);
    ffprintf("Device size: %.1f GB!\n", (float)device_size/2/1024/1024);
    thread_count = atoi(argv[6]);


    //configure trace_read thread---------------------------------------------------------------------------------   
    struct Reading_Thread_Info* reading_thread_info = malloc(sizeof(struct Reading_Thread_Info));
    memset(reading_thread_info, 0, sizeof(struct Reading_Thread_Info));

    char *split_left, *split_right;
    split_left = strtok_r(argv[4], "-", &split_right);
    if(strcmp(split_left, "webvm") == 0) {
        MD5LenPerPage = 32;
        sprintf(reading_thread_info->trace_path, "%s/webvm/webmail+online.cs.fiu.edu-110108-113008.", argv[3]);
    } else if(strcmp(split_left, "homes") == 0) {
        MD5LenPerPage = 32 * 8;
        sprintf(reading_thread_info->trace_path, "%s/homes/homes-110108-112108.", argv[3]);
    } else if(strcmp(split_left, "mail") == 0) {
        MD5LenPerPage = 32;
        sprintf(reading_thread_info->trace_path, "%s/mail/cheetah.cs.fiu.edu-110108-113008.", argv[3]);
    } else {
        ffprintf("Unknow Trace: %s!\n", split_left);
        return 0;
    }

    if (split_right == NULL)           reading_thread_info->trace_id = 0;
    else if (isdigitstr(split_right))  reading_thread_info->trace_id = atoi(split_right);
    else{
        ffprintf("Unknow Trace: %s-%s!\n", split_left, split_right);
        return 0;
    }
    
    init_queue(&IO_queue);
    memory_pool_init(&mp, QUEUE_SIZE);

    trace_finish = false;
    pthread_create(&reading_thread_info->thread_pid, NULL, workload_read, reading_thread_info);


    //configure IOPS_statistic thread-----------------------------------------------------------------------------
    struct Print_Thread_Info* print_thread_info = malloc(sizeof(struct Print_Thread_Info));
    memset(print_thread_info, 0, sizeof(struct Print_Thread_Info));

    atomic_store(&workload_finish, 0);
    strcpy(print_thread_info->outfile_name, argv[5]);
    pthread_create(&print_thread_info->thread_pid, NULL, print_info_per_second, print_thread_info);


    //configure workload_running thread-----------------------------------------------------------------------------
    struct Running_Thread_Info* running_thread_info = malloc(sizeof(struct Running_Thread_Info)*thread_count);
    memset(running_thread_info, 0, sizeof(struct Running_Thread_Info)*thread_count);
    for(int thread_id=0; thread_id<thread_count; thread_id++)
    {
        running_thread_info[thread_id].id = thread_id+1;
        // running_thread_info[thread_id].lba_num = device_size / thread_count / 8 * 8;     //4KB page align
        // running_thread_info[thread_id].min_lba = running_thread_info[thread_id].lba_num * thread_id;
        // running_thread_info[thread_id].lba_num = device_size / 8 * 8;     //4KB page align
        // running_thread_info[thread_id].min_lba = 0;
        strcpy(running_thread_info[thread_id].device_name, argv[1]);
        
        pthread_create(&running_thread_info[thread_id].thread_pid, NULL, running_workload, &running_thread_info[thread_id]);
    }
    
    //waiting threads finish---------------------------------------------------------------------------------------
    pthread_join(reading_thread_info->thread_pid, NULL);
    for(int thread_id=0; thread_id<thread_count; thread_id++)
    {
        pthread_join(running_thread_info[thread_id].thread_pid, NULL);
    }
    pthread_join(print_thread_info->thread_pid, NULL);

    //printf final IOPS
    time_t current_time = time(NULL);
    float tt_running_time = difftime(current_time, begin_time);
    fprintf(IOPS_fp, "result: SumReadIOPS:%ld SumWriteIOPS:%ld AvgReadIOPS:%.0f AvgWriteIOPS:%.0f\n",
            statisic.sum_IOPS[1], statisic.sum_IOPS[0], statisic.sum_IOPS[1]/tt_running_time, statisic.sum_IOPS[0]/tt_running_time);
    fflush(IOPS_fp);
    fclose(IOPS_fp);
    printf("result: SumReadIOPS:%ld SumWriteIOPS:%ld AvgReadIOPS:%.0f AvgWriteIOPS:%.0f\n",
            statisic.sum_IOPS[1], statisic.sum_IOPS[0], statisic.sum_IOPS[1]/tt_running_time, statisic.sum_IOPS[0]/tt_running_time);
    
    fclose(out_fp);
    return 0;
}
