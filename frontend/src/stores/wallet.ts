import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api'

export const useWalletStore = defineStore('wallet', () => {
  const balanceCents = ref(0)
  const totalRechargeCents = ref(0)
  const totalSpendCents = ref(0)
  const loaded = ref(false)

  async function refresh() {
    try {
      const w = await api.getWallet()
      balanceCents.value = w.balanceCents
      totalRechargeCents.value = w.totalRechargeCents
      totalSpendCents.value = w.totalSpendCents
      loaded.value = true
    } catch {
      /* 未登录时忽略 */
    }
  }

  function setBalance(cents: number) {
    balanceCents.value = cents
  }

  function clear() {
    balanceCents.value = 0
    totalRechargeCents.value = 0
    totalSpendCents.value = 0
    loaded.value = false
  }

  return { balanceCents, totalRechargeCents, totalSpendCents, loaded, refresh, setBalance, clear }
})
