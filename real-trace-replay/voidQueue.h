#ifndef _VOID_QUEUE_H_
#define _VOID_QUEUE_H_
 
 
 
typedef enum{
    FALSE = 0,
    TRUE
}BOOL;
 
typedef struct myQueue{
    void  *data; // 数据域
    int size;    // 数据域的大小
    int Front;
    int Rear;
    int MaxLen;  // 队列的最大长度
}voidQueue;
 
 
typedef void (queue_op_t)(void *);
typedef int (queue_cmp_t)(void *, void *);
 
 
BOOL initQueue(voidQueue *pQueue, int size, int maxLen);  // 初始化队列
BOOL isFullQueue(voidQueue *pQueue); // 判断队列是否已满
BOOL isEmptyQueue(voidQueue *pQueue);// 判断队列是否已空
BOOL enQueue(voidQueue *pQueue, void *data); // 添加数据
BOOL delQueue(voidQueue *pQueue, void *data); // 获取数据
void traverseQueue(voidQueue *pQueue, queue_op_t * opt); // 遍历队列
void destroyedQueue(voidQueue *pQueue);
int getQueueCount(voidQueue *pQueue); // 得到队列的当前长度
 
 
#endif