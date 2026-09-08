-- One hash tag keeps state, queues and metric mutations in the same Redis slot.
local p=KEYS[1]
local op=ARGV[1]
local now=tonumber(ARGV[2])
local id=ARGV[3]
local key=p..'state:'..id
local function persist(s)
  redis.call('SET',key,cjson.encode(s),'PXAT',s.receivedAt+86400000)
end
local function metric(s,field,task)
  for _,b in ipairs(s.bindings) do
    if not task or task==b.taskId then
      local mk=p..'metric:'..math.floor(s.receivedAt/3600000)..':'..s.mediaId..':'..b.taskId..':'..s.upstream.id
      redis.call('HSET',mk,'mediaId',s.mediaId,'advertiserId',s.advertiserId,'taskId',b.taskId,'upstreamId',s.upstream.id,'timeHour',math.floor(s.receivedAt/3600000)*3600000)
      redis.call('HINCRBY',mk,field,1)
      redis.call('HINCRBY',mk,'version',1)
      redis.call('PERSIST',mk)
      redis.call('SADD',p..'metric-dirty',mk)
    end
  end
end
if op=='INIT' then
  local s=cjson.decode(ARGV[4])
  local ik=p..'idem:'..s.mediaId..':'..s.requestId
  local old=redis.call('GET',ik)
  if old then
    local value=redis.call('GET',p..'state:'..old)
    if not value then return 'EXPIRED' end
    if cjson.decode(value).requestHash~=s.requestHash then return 'CONFLICT' end
    return value
  end
  persist(s)
  redis.call('SET',ik,id,'PX',86400000)
  redis.call('ZADD',p..'send',now,id)
  redis.call('ZADD',p..'deadline',s.deadline,id)
  for _,idx in ipairs({'all','media:'..s.mediaId,'advertiser:'..s.advertiserId}) do
    redis.call('ZADD',p..'records:'..idx,s.receivedAt,id)
    redis.call('ZREMRANGEBYSCORE',p..'records:'..idx,'-inf',now-86400000)
    redis.call('EXPIRE',p..'records:'..idx,86400)
  end
  metric(s,'requestCount')
  return cjson.encode(s)
end
local value=redis.call('GET',key)
if not value then
  for _,q in ipairs({'send','deadline','notify'}) do redis.call('ZREM',p..q,id) end
  return 'MISSING'
end
local s=cjson.decode(value)
if op=='FINISH' then
  local status=ARGV[4]
  local hash=ARGV[7]
  if s.status~='PENDING' then
    if s.status=='TIMEOUT' then return 'EXPIRED' end
    if s.decisionHash==hash then return cjson.encode(s) end
    return 'CONFLICT'
  end
  if now>=s.deadline then status='TIMEOUT' end
  if status=='TIMEOUT' and now<s.deadline then return cjson.encode(s) end
  s.status=status
  s.reasonCode=status=='TIMEOUT' and 'RTA_TIMEOUT' or ARGV[5]
  s.decisionHash=hash
  s.completedAt=now
  s.deliveryDeadline=now+30000
  s.expiresAt=now+s.upstream.admissionSeconds*1000
  s.encryptedPayload=nil
  s.matchedTaskIds=status=='ALLOW' and cjson.decode(ARGV[6]) or {}
  for _,b in ipairs(s.bindings) do
    local field=string.lower(status)..'Count'
    if status=='ALLOW' then
      field='denyCount'
      for _,t in ipairs(s.matchedTaskIds) do if t==b.taskId then field='allowCount' end end
    end
    metric(s,field,b.taskId)
  end
  redis.call('ZREM',p..'send',id)
  redis.call('ZREM',p..'deadline',id)
  redis.call('ZADD',p..'notify',now,id)
  persist(s)
elseif op=='SUBMITTED' then
  if s.status=='PENDING' then s.encryptedPayload=nil; persist(s) end
  redis.call('ZREM',p..'send',id)
elseif op=='SEND_RETRY' then
  if s.status=='PENDING' then
    s.upstreamAttempts=s.upstreamAttempts+1
    persist(s)
    redis.call('ZADD',p..'send',math.min(s.deadline,now+250),id)
  end
elseif op=='DELIVERY' then
  if s.status=='PENDING' or s.deliveryStatus~='PENDING' then return cjson.encode(s) end
  s.deliveryAttempts=s.deliveryAttempts+1
  if ARGV[4]=='true' then
    s.deliveryStatus='DELIVERED'
    metric(s,'deliverySuccessCount')
  elseif now>=s.deliveryDeadline or s.deliveryAttempts>=4 then
    s.deliveryStatus='FAILED'
    metric(s,'deliveryFailedCount')
  end
  if s.deliveryStatus~='PENDING' then redis.call('ZREM',p..'notify',id)
  else
    local delays={1000,3000,10000}
    redis.call('ZADD',p..'notify',math.min(s.deliveryDeadline,now+delays[s.deliveryAttempts]),id)
  end
  persist(s)
end
return cjson.encode(s)
