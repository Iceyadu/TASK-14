import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DataTable from '../../src/components/shared/DataTable.vue'

/**
 * Unit tests for the DataTable component covering loading, empty, error,
 * retry, and data-rendering states.
 */

const columns = [
  { key: 'id', label: 'ID' },
  { key: 'name', label: 'Name' },
  { key: 'status', label: 'Status' },
]

describe('TableStates', () => {
  it('testLoadingState', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], loading: true }
    })

    // Should show spinner
    expect(wrapper.find('.spinner').exists()).toBe(true)
    expect(wrapper.text()).toContain('Loading')

    // Should NOT show table
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('testEmptyState', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], loading: false, emptyMessage: 'No exam sessions found.' }
    })

    expect(wrapper.find('.state-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('No exam sessions found.')
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('testErrorState', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], loading: false, error: 'Failed to load data.' }
    })

    expect(wrapper.find('.state-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('Failed to load data.')

    // Retry button should be present
    const retryButton = wrapper.find('.state-error button')
    expect(retryButton.exists()).toBe(true)
    expect(retryButton.text()).toBe('Retry')
  })

  it('testRetryButton', async () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], loading: false, error: 'Network error' }
    })

    const retryButton = wrapper.find('.state-error button')
    await retryButton.trigger('click')

    // Should emit 'retry' event
    expect(wrapper.emitted('retry')).toBeTruthy()
    expect(wrapper.emitted('retry').length).toBe(1)
  })

  it('testDataRendered', () => {
    const rows = [
      { id: 1, name: 'Midterm Exam', status: 'PUBLISHED' },
      { id: 2, name: 'Final Exam', status: 'DRAFT' },
      { id: 3, name: 'Quiz 1', status: 'APPROVED' },
    ]

    const wrapper = mount(DataTable, {
      props: { columns, rows, loading: false }
    })

    // Table should be rendered
    const table = wrapper.find('table')
    expect(table.exists()).toBe(true)

    // Header row
    const headers = wrapper.findAll('th')
    expect(headers.length).toBe(3)
    expect(headers[0].text()).toBe('ID')
    expect(headers[1].text()).toBe('Name')
    expect(headers[2].text()).toBe('Status')

    // Data rows
    const dataRows = wrapper.findAll('tbody tr')
    expect(dataRows.length).toBe(3)
    expect(dataRows[0].text()).toContain('Midterm Exam')
    expect(dataRows[1].text()).toContain('DRAFT')
  })
})
