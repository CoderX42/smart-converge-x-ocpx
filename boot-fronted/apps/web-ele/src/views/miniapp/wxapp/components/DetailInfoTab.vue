<script lang="ts" setup>
import { ElCard, ElCol, ElImage, ElRow, ElTag } from 'element-plus';

interface Props {
  miniBaseInfoData: any;
  miniIcpEntranceData: any;
}

const props = defineProps<Props>();

// 获取状态文本
const getStatusText = (status: number) => {
  return status === 1 ? '已认证' : '未认证';
};

// 获取备案状态文本
const getIcpStatusText = (status: number) => {
  switch (status) {
    case 2: {
      return '平台审核中';
    }
    case 3: {
      return '平台审核驳回';
    }
    case 4: {
      return '管局审核中';
    }
    case 5: {
      return '管局审核驳回';
    }
    case 6: {
      return '已备案';
    }
    case 1024: {
      return '未备案';
    }
    case 1025: {
      return '未备案(小程序信息未填)';
    }
    case 1026: {
      return '未备案(小程序类目未填)';
    }
    case 1027: {
      return '未备案(小程序信息未填且类目未填)';
    }
    case 1028: {
      return '未备案(小程序未认证)';
    }
    case 1029: {
      return '未备案(小程序信息未填且未认证)';
    }
    case 1030: {
      return '未备案(小程序类目未填且未认证)';
    }
    case 1031: {
      return '未备案(小程序信息未填且类目未填且未认证)';
    }
    default: {
      return '未知状态';
    }
  }
};
</script>

<template>
  <div class="mini-info-container">
    <ElCard class="mini-info-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="header-icon">
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="currentColor"
              >
                <path
                  d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"
                />
              </svg>
            </div>
            <span class="header-title">小程序详细信息</span>
          </div>
          <div class="header-status">
            <ElTag type="success" size="small">已授权</ElTag>
          </div>
        </div>
      </template>

      <ElRow :gutter="24">
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">小程序名称</span>
            </div>
            <div class="segment-two">
              {{ miniBaseInfoData.nickname }}
            </div>
            <div class="segment-three">
              修改次数：{{
                miniBaseInfoData.nickname_info?.modify_used_count || 0
              }}/{{ miniBaseInfoData.nickname_info?.modify_quota || 0 }}
            </div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">小程序头像</span>
            </div>
            <div class="segment-two avatar-container">
              <ElImage
                v-if="miniBaseInfoData.head_image_info?.head_image_url"
                :src="miniBaseInfoData.head_image_info.head_image_url"
                style="width: 60px; height: 60px; border-radius: 8px"
                fit="cover"
              />
              <span v-else>暂无头像</span>
            </div>
            <div class="segment-three">
              修改次数：{{
                miniBaseInfoData.head_image_info?.modify_used_count || 0
              }}/{{ miniBaseInfoData.head_image_info?.modify_quota || 0 }}
            </div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">介绍</span>
            </div>
            <div class="segment-two">
              {{ miniBaseInfoData.signature_info?.signature || '暂无' }}
            </div>
            <div class="segment-three">
              修改次数：{{
                miniBaseInfoData.signature_info?.modify_used_count || 0
              }}/{{ miniBaseInfoData.signature_info?.modify_quota || 0 }}
            </div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">AppID</span>
            </div>
            <div class="segment-two">
              {{ miniBaseInfoData.appid }}
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">主体类型</span>
            </div>
            <div class="segment-two">
              {{ miniBaseInfoData.principal_type === 0 ? '个人' : '企业' }}
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">主体名称</span>
            </div>
            <div class="segment-two">
              {{ miniBaseInfoData.principal_name }}
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">实名认证</span>
            </div>
            <div class="segment-two">
              <span
                :class="
                  miniBaseInfoData.realname_status === 1
                    ? 'status-success'
                    : 'status-error'
                "
              >
                {{ getStatusText(miniBaseInfoData.realname_status) }}
              </span>
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">注册国家</span>
            </div>
            <div class="segment-two">
              {{
                miniBaseInfoData.registered_country === 1017 ? '中国' : '其他'
              }}
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">微信认证</span>
            </div>
            <div class="segment-two">
              <span
                :class="
                  miniBaseInfoData.wx_verify_info?.qualification_verify
                    ? 'status-success'
                    : 'status-error'
                "
              >
                {{
                  miniBaseInfoData.wx_verify_info?.qualification_verify
                    ? '已认证'
                    : '未认证'
                }}
              </span>
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
        <ElCol :span="24">
          <div class="info-item info-item-three-segments">
            <div class="info-label segment-one">
              <span class="label-text">微信备案</span>
            </div>
            <div class="segment-two">
              <span
                v-if="miniIcpEntranceData"
                :class="
                  miniIcpEntranceData.status === 6
                    ? 'status-success'
                    : 'status-error'
                "
              >
                {{ getIcpStatusText(miniIcpEntranceData.status) }}
              </span>
              <span v-else class="status-error">暂无数据</span>
            </div>
            <div class="segment-three">&nbsp;</div>
          </div>
        </ElCol>
      </ElRow>
    </ElCard>
  </div>
</template>

<style scoped>
.mini-info-container {
  padding: 20px 0;
}

.mini-info-card {
  margin-bottom: 20px;
  overflow: hidden;
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.header-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: white;
  background: rgb(255 255 255 / 20%);
  border-radius: 8px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 16px;
  background: white;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.info-item-three-segments {
  display: flex;
  gap: 16px;
  align-items: center;
}

.segment-one {
  flex: 0 0 120px;
  min-width: 120px;
}

.segment-two {
  flex: 1;
  font-size: 14px;
  color: #4a4a4a;
}

.segment-three {
  flex: 0 0 120px;
  padding: 4px 8px;
  font-size: 12px;
  color: #909399;
  text-align: right;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.status-success {
  font-weight: 500;
  color: #67c23a;
}

.status-error {
  font-weight: 500;
  color: #f56c6c;
}

.info-item:hover {
  box-shadow: 0 4px 12px rgb(0 0 0 / 10%);
  transform: translateY(-2px);
}

.info-label {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.label-text {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
}

.avatar-container {
  display: flex;
  align-items: center;
}
</style>
