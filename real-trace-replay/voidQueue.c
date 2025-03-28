#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "voidQueue.h"
 
BOOL initQueue(voidQueue *pQueue, int size, int maxLen)
{
    pQueue->data = malloc(size * (maxLen+1));
    memset(pQueue->data, 0, size * (maxLen+1));
    if (NULL == pQueue->data)
    {
        return FALSE;
    }
 
    pQueue->Front = 0;
    pQueue->Rear = 0;
    pQueue->size = size;
    pQueue->MaxLen = maxLen + 1;
 
    return TRUE;
}
 
 
BOOL isFullQueue(voidQueue *pQueue)
{
    if(pQueue->Front == (pQueue->Rear + 1) % pQueue->MaxLen)
    {
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}
 
BOOL isEmptyQueue(voidQueue *pQueue)
{
    if (pQueue->Front == pQueue->Rear)
    {
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}
 
BOOL enQueue(voidQueue *pQueue, void *data)
{
    if (isFullQueue(pQueue))
    {
        return FALSE;
    }
 
    memcpy(pQueue->data + pQueue->size * pQueue->Rear, data, pQueue->size);
    pQueue->Rear = (pQueue->Rear + 1) % pQueue->MaxLen;
 
    return TRUE;
}
 
BOOL delQueue(voidQueue *pQueue, void *data)
{
    if (isEmptyQueue(pQueue))
    {
        return FALSE;
    }
 
    memcpy(data, pQueue->data + pQueue->size * pQueue->Front, pQueue->size);
    memset(pQueue->data + pQueue->size * pQueue->Front, 0, pQueue->size);
    pQueue->Front = (pQueue->Front + 1) % pQueue->MaxLen;
 
    return TRUE;
}
 
void traverseQueue(voidQueue *pQueue, queue_op_t *opt)
{
    if (isEmptyQueue(pQueue))
    {
        return ;
    }
 
    int i = pQueue->Front;
    printf("queue element start: \n");
    while (i % pQueue->MaxLen != pQueue->Rear)
    {
        opt(pQueue->data + (i % pQueue->MaxLen) * pQueue->size);
        i++;
    }
 
    printf("traverse end\n");
}
 
int getQueueCount(voidQueue *pQueue)
{
    int interval = pQueue->Rear - pQueue->Front;
    return interval >= 0 ? interval : interval + pQueue->MaxLen;

    int count = 0;
 
    if (isEmptyQueue(pQueue))
    {
        return count;
    }
 
    int i = pQueue->Front;
    while (i % pQueue->MaxLen != pQueue->Rear)
    {
        i++;
        count++;
    }
 
    return count;
}
 
void destroyedQueue(voidQueue *pQueue)
{
    if (NULL != pQueue->data)
    {
        free(pQueue->data);
        pQueue->data = NULL;
    }
}