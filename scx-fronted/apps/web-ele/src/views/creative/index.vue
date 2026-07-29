<script lang="ts" setup>
import type { Media } from '#/api/ad';
import type { Organization } from '#/api/commercial';
import type { CreativeAsset, LandingPage, ReviewCase } from '#/api/creative';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { getMedia } from '#/api/ad';
import { getOrganizations } from '#/api/commercial';
import {
  createAsset,
  getAssets,
  getLandingPages,
  getReviewCases,
  publishAsset,
  publishLandingPage,
  reviewAsset,
  saveLandingPage,
  submitAsset,
} from '#/api/creative';

import '../business-domain.css';

defineOptions({ name: 'CreativeWorkspace' });

const route = useRoute();
const media = ref<Media[]>([]);
const organizations = ref<Organization[]>([]);
const mediaId = ref('');
const orgId = ref('');
const keyword = ref('');
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const assetRows = ref<CreativeAsset[]>([]);
const reviewRows = ref<ReviewCase[]>([]);
const landingRows = ref<LandingPage[]>([]);
const drawer = ref(false);
const form = reactive<Record<string, number | string | undefined>>({});
const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const isAssets = computed(() => sectionKey.value === 'assets');
const isReviews = computed(() => sectionKey.value === 'reviews');

async function bootstrap() {
  const [mediaResult, orgResult] = await Promise.all([
    getMedia({ current: 1, size: 200 }),
    getOrganizations({ current: 1, size: 200 }),
  ]);
  media.value = mediaResult.records;
  organizations.value = orgResult.records;
  mediaId.value = media.value[0]?.id || '';
  orgId.value = organizations.value[0]?.id || '';
}

async function load() {
  if (!orgId.value || ((isAssets.value || isReviews.value) && !mediaId.value)) return;
  loading.value = true;
  try {
    const query = {
      current: current.value,
      keyword: keyword.value || undefined,
      mediaId: mediaId.value,
      orgId: orgId.value,
      size: size.value,
      status: status.value || undefined,
    };
    if (isAssets.value) {
      const result = await getAssets(query);
      assetRows.value = result.records;
      total.value = result.total;
    } else if (isReviews.value) {
      const result = await getReviewCases(query);
      reviewRows.value = result.records;
      total.value = result.total;
    } else {
      const result = await getLandingPages(query);
      landingRows.value = result.records;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}

function openAsset() {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, {
    assetType: 'IMAGE',
    fileSize: 1,
    mediaId: mediaId.value,
    mimeType: 'image/jpeg',
    orgId: orgId.value,
  });
  drawer.value = true;
}

function openLanding(row?: LandingPage) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      orgId: orgId.value,
      pageType: 'EXTERNAL',
      schemaVersion: 1,
    },
  );
  drawer.value = true;
}

async function submit() {
  saving.value = true;
  try {
    if (isAssets.value) {
      const required = [
        form.assetName,
        form.assetType,
        form.fileUrl,
        form.fileHash,
      ];
      if (required.some((value) => !value)) {
        ElMessage.warning('请完整填写素材名称、类型、URL 和 SHA-256');
        return;
      }
      await createAsset({
        assetName: String(form.assetName),
        assetType: String(form.assetType),
        fileHash: String(form.fileHash),
        fileSize: Number(form.fileSize),
        fileUrl: String(form.fileUrl),
        mediaId: mediaId.value,
        mimeType: String(form.mimeType),
        orgId: orgId.value,
      });
      ElMessage.success('素材已创建');
    } else {
      if (!form.pageName || !form.targetUrl) {
        ElMessage.warning('请填写落地页名称和目标地址');
        return;
      }
      await saveLandingPage(form);
      ElMessage.success('落地页已保存');
    }
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function assetAction(row: CreativeAsset, action: 'publish' | 'submit') {
  await ElMessageBox.confirm(
    action === 'submit' ? '确认提交素材审核？' : '确认发布该素材？',
    '状态确认',
  );
  await (action === 'submit' ? submitAsset(row.id) : publishAsset(row.id));
  ElMessage.success('素材状态已更新');
  await load();
}

async function decide(row: ReviewCase, decision: 'APPROVE' | 'REJECT') {
  await ElMessageBox.confirm(
    `确认${decision === 'APPROVE' ? '通过' : '驳回'}该素材？`,
    '审核确认',
  );
  await reviewAsset(row.id, decision);
  ElMessage.success('审核已完成');
  await load();
}

async function publishLanding(row: LandingPage) {
  await ElMessageBox.confirm('发布后将允许创意绑定使用，确认继续？', '发布落地页');
  await publishLandingPage(row.id);
  ElMessage.success('落地页已发布');
  await load();
}

watch([mediaId, orgId, () => route.path], () => {
  current.value = 1;
  void load();
});
bootstrap().then(load);
</script>

<template>
  <main class="domain-page">
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">CREATIVE · ASSET LIFECYCLE</p>
        <h1 class="domain-title">
          {{ isAssets ? '素材库' : isReviews ? '素材审核' : '落地页' }}
        </h1>
        <p class="domain-description">
          素材文件、不可变版本、安全扫描、人工审核和落地页发布均保留完整状态记录。
        </p>
      </div>
      <el-button
        v-if="isAssets"
        v-access:code="'creative:asset:write'"
        type="primary"
        @click="openAsset"
      >
        URL 新增素材
      </el-button>
      <el-button
        v-else-if="!isReviews"
        v-access:code="'creative:landing-page:write'"
        type="primary"
        @click="openLanding()"
      >
        新增落地页
      </el-button>
    </header>

    <el-card class="domain-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-select
            v-model="orgId"
            filterable
            placeholder="选择组织"
            style="width: 220px"
          >
            <el-option
              v-for="item in organizations"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-if="isAssets || isReviews"
            v-model="mediaId"
            filterable
            placeholder="选择媒体"
            style="width: 200px"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或关键字"
            style="width: 220px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审核中" value="REVIEWING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-if="isAssets" v-loading="loading" :data="assetRows" row-key="id">
        <el-table-column label="素材名称" prop="assetName" min-width="190" />
        <el-table-column label="类型" prop="assetType" width="120" />
        <el-table-column label="文件地址" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="domain-code">{{ row.fileUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="120" />
        <el-table-column align="right" label="操作" width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'UPLOADED'"
              v-access:code="'creative:asset:write'"
              link
              type="primary"
              @click="assetAction(row, 'submit')"
            >
              送审
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-access:code="'creative:asset:write'"
              link
              type="success"
              @click="assetAction(row, 'publish')"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="isReviews"
        v-loading="loading"
        :data="reviewRows"
        row-key="id"
      >
        <el-table-column label="审核单" min-width="190">
          <template #default="{ row }">
            <span class="domain-code">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="素材 ID" prop="assetId" min-width="190" />
        <el-table-column label="安全扫描" prop="virusScanStatus" width="130" />
        <el-table-column label="提交时间" prop="submittedAt" min-width="170" />
        <el-table-column label="状态" prop="status" width="120" />
        <el-table-column align="right" label="操作" width="160">
          <template #default="{ row }">
            <template v-if="row.status === 'SUBMITTED'">
              <el-button
                v-access:code="'creative:review'"
                link
                type="success"
                @click="decide(row, 'APPROVE')"
              >
                通过
              </el-button>
              <el-button
                v-access:code="'creative:review'"
                link
                type="danger"
                @click="decide(row, 'REJECT')"
              >
                驳回
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="landingRows" row-key="id">
        <el-table-column label="落地页名称" prop="pageName" min-width="190" />
        <el-table-column label="类型" prop="pageType" width="130" />
        <el-table-column label="目标地址" prop="targetUrl" min-width="280" show-overflow-tooltip />
        <el-table-column label="版本" prop="schemaVersion" width="90" />
        <el-table-column label="状态" prop="status" width="120" />
        <el-table-column align="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'creative:landing-page:write'"
              link
              @click="openLanding(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'creative:landing-page:write'"
              link
              type="success"
              @click="publishLanding(row)"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="domain-pagination">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawer"
      :title="isAssets ? '新增素材' : form.id ? '编辑落地页' : '新增落地页'"
      size="520px"
    >
      <el-form label-position="top" :model="form">
        <template v-if="isAssets">
          <el-form-item label="素材名称" required>
            <el-input v-model="form.assetName" />
          </el-form-item>
          <el-form-item label="素材类型" required>
            <el-select v-model="form.assetType" class="w-full">
              <el-option label="图片" value="IMAGE" />
              <el-option label="视频" value="VIDEO" />
              <el-option label="HTML" value="HTML" />
            </el-select>
          </el-form-item>
          <el-form-item label="文件 URL" required>
            <el-input v-model="form.fileUrl" />
          </el-form-item>
          <el-form-item label="MIME 类型">
            <el-input v-model="form.mimeType" />
          </el-form-item>
          <el-form-item label="文件大小（字节）">
            <el-input-number v-model="form.fileSize" :min="1" class="!w-full" />
          </el-form-item>
          <el-form-item label="SHA-256" required>
            <el-input v-model="form.fileHash" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="落地页名称" required>
            <el-input v-model="form.pageName" />
          </el-form-item>
          <el-form-item label="页面类型">
            <el-select v-model="form.pageType" class="w-full">
              <el-option label="外部链接" value="EXTERNAL" />
              <el-option label="托管页面" value="HOSTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标地址" required>
            <el-input v-model="form.targetUrl" />
          </el-form-item>
          <el-form-item label="页面内容（JSON）">
            <el-input v-model="form.content" :rows="5" type="textarea" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">保存</el-button>
      </template>
    </el-drawer>
  </main>
</template>
