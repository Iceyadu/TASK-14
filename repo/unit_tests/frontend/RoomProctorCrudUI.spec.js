import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import RoomList from '../../frontend/src/views/rooms/RoomList.vue'
import ProctorList from '../../frontend/src/views/proctors/ProctorList.vue'

const fixtures = vi.hoisted(() => {
  const roomApi = {
    listCampuses: vi.fn(),
    listRooms: vi.fn(),
    updateRoom: vi.fn()
  }
  const proctorApi = {
    list: vi.fn(),
    create: vi.fn(),
    remove: vi.fn()
  }
  const examSessionApi = {
    list: vi.fn(() => Promise.resolve({ data: { items: [], totalPages: 1 } }))
  }
  return { roomApi, proctorApi, examSessionApi }
})

vi.mock('../../frontend/src/api/client.js', () => ({
  roomApi: fixtures.roomApi,
  proctorApi: fixtures.proctorApi,
  examSessionApi: fixtures.examSessionApi
}))

const DataTableScopedStub = {
  name: 'DataTable',
  props: ['rows'],
  emits: ['retry'],
  template: `
    <div>
      <div v-for="row in rows" :key="row.id" class="row-slot">
        <slot name="cell-capacity" :value="row.capacity"></slot>
        <slot name="cell-actions" :row="row"></slot>
      </div>
    </div>
  `
}

describe('RoomList CRUD UI', () => {
  beforeEach(() => {
    fixtures.roomApi.listCampuses.mockResolvedValue({
      data: [{ id: 1, name: 'Main Campus' }]
    })
    fixtures.roomApi.listRooms.mockResolvedValue({
      data: [
        { id: 10, name: 'Lab A', capacity: 30, building: 'Block 1', floor: '1' },
        { id: 11, name: 'Hall B', capacity: 100, building: 'Block 2', floor: '2' }
      ]
    })
    fixtures.roomApi.updateRoom.mockResolvedValue({ data: {} })
  })

  it('loads campuses on mount and renders room rows', async () => {
    const wrapper = mount(RoomList, {
      attachTo: document.body,
      global: { stubs: { DataTable: DataTableScopedStub, Teleport: false } }
    })
    await flushPromises()

    expect(fixtures.roomApi.listCampuses).toHaveBeenCalled()
    expect(fixtures.roomApi.listRooms).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.row-slot').length).toBe(2)
  })

  it('edit dialog opens with room prefilled and cancels without API call', async () => {
    const wrapper = mount(RoomList, {
      attachTo: document.body,
      global: { stubs: { DataTable: DataTableScopedStub, Teleport: false } }
    })
    await flushPromises()

    const editButtons = wrapper.findAll('button.btn-secondary.btn-sm')
    await editButtons[0].trigger('click')

    expect(wrapper.find('.dialog-box').exists()).toBe(true)
    expect(wrapper.find('.dialog-box input').element.value).toBe('Lab A')

    await wrapper.find('.dialog-box .btn-secondary').trigger('click')
    expect(wrapper.find('.dialog-box').exists()).toBe(false)
    expect(fixtures.roomApi.updateRoom).not.toHaveBeenCalled()
  })

  it('edit dialog shows validation error when name is cleared', async () => {
    const wrapper = mount(RoomList, {
      attachTo: document.body,
      global: { stubs: { DataTable: DataTableScopedStub, Teleport: false } }
    })
    await flushPromises()

    await wrapper.findAll('button.btn-secondary.btn-sm')[0].trigger('click')

    const nameInput = wrapper.find('.dialog-box input')
    await nameInput.setValue('')

    await wrapper.find('.dialog-box form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Room name is required.')
    expect(fixtures.roomApi.updateRoom).not.toHaveBeenCalled()
  })

  it('edit dialog submits updated name and calls updateRoom', async () => {
    const wrapper = mount(RoomList, {
      attachTo: document.body,
      global: { stubs: { DataTable: DataTableScopedStub, Teleport: false } }
    })
    await flushPromises()

    await wrapper.findAll('button.btn-secondary.btn-sm')[0].trigger('click')

    const nameInput = wrapper.find('.dialog-box input')
    await nameInput.setValue('Lab A Renamed')

    await wrapper.find('.dialog-box form').trigger('submit.prevent')
    await flushPromises()

    expect(fixtures.roomApi.updateRoom).toHaveBeenCalledWith(10, expect.objectContaining({
      name: 'Lab A Renamed'
    }))
  })
})

describe('ProctorList validation UI', () => {
  beforeEach(() => {
    fixtures.proctorApi.list.mockResolvedValue({
      data: { items: [], totalPages: 1, totalItems: 0 }
    })
    fixtures.proctorApi.create.mockResolvedValue({ data: {} })
    fixtures.proctorApi.remove.mockResolvedValue({ data: {} })
    fixtures.examSessionApi.list.mockResolvedValue({
      data: { items: [], totalPages: 1 }
    })
  })

  it('assign proctor dialog shows validation error when fields are empty', async () => {
    const wrapper = mount(ProctorList, {
      attachTo: document.body,
      global: {
        stubs: {
          DataTable: DataTableScopedStub,
          Teleport: false
        }
      }
    })
    await flushPromises()

    await wrapper.get('button.btn-primary').trigger('click')
    await wrapper.get('.dialog-actions .btn-primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Please select both a proctor and a session.')
    expect(fixtures.proctorApi.create).not.toHaveBeenCalled()
  })
})
